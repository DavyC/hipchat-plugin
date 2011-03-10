package jenkins.plugins.hipchat;

import hudson.model.Result;
import hudson.model.AbstractBuild;
import hudson.model.CauseAction;
import hudson.scm.ChangeLogSet;
import hudson.scm.ChangeLogSet.AffectedFile;
import hudson.scm.ChangeLogSet.Entry;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import org.apache.commons.lang.StringUtils;

@SuppressWarnings("rawtypes")
public class ActiveNotifier implements FineGrainedNotifier {

   private static final Logger logger = Logger.getLogger(HipChatListener.class.getName());

   HipChatNotifier notifier;
   HipChatService hipChat;

   public ActiveNotifier(HipChatNotifier notifier) {
      super();
      this.notifier = notifier;
      this.hipChat = notifier.newHipChatService();
   }

   public void deleted(AbstractBuild r) {}

   public void started(AbstractBuild r) {
      String changes = getChanges(r);
      CauseAction cause = r.getAction(CauseAction.class);
      if(changes != null) {
         this.hipChat.publish(changes);
      }
      else if(cause != null) {
         MessageBuilder message = new MessageBuilder(notifier, r);
         message.append(cause.getShortDescription());
         this.hipChat.publish(message.appendOpenLink().toString());
      }
      else {
         this.hipChat.publish(getBuildStatusMessage(r));
      }
   }

   public void finalized(AbstractBuild r) {}

   public void completed(AbstractBuild r) {
      this.hipChat.publish(getBuildStatusMessage(r));
   }

   String getChanges(AbstractBuild r) {
      if(!r.hasChangeSetComputed()) {
         logger.info("No change set computed...");
         return null;
      }
      ChangeLogSet changeSet = r.getChangeSet();
      List<Entry> entries = new LinkedList<Entry>();
      Set<AffectedFile> files = new HashSet<AffectedFile>();
      for(Object o : changeSet.getItems()) {
         Entry entry = (Entry)o;
         logger.info("Entry " + o);
         entries.add(entry);
         files.addAll(entry.getAffectedFiles());
      }
      if(entries.isEmpty()) {
         logger.info("Empty change...");
         return null;
      }
      Set<String> authors = new HashSet<String>();
      for(Entry entry : entries) {
         authors.add(entry.getAuthor().getDisplayName());
      }
      MessageBuilder message = new MessageBuilder(notifier, r);
      message.append("Started by changes from ");
      message.append(StringUtils.join(authors, ", "));
      message.append(" (");
      message.append(files.size());
      message.append(" file(s) changed)");
      return message.appendOpenLink().toString();
   }

   String getBuildStatusMessage(AbstractBuild r) {
      MessageBuilder message = new MessageBuilder(notifier, r);
      message.appendStatusMessage();
      message.appendDuration();
      return message.appendOpenLink().toString();
   }

   public static class MessageBuilder {
      private StringBuffer message;
      private HipChatNotifier notifier;
      private AbstractBuild build;

      public MessageBuilder(HipChatNotifier notifier, AbstractBuild build) {
         this.notifier = notifier;
         this.message = new StringBuffer();
         this.build = build;
         startMessage();
      }

      public MessageBuilder appendStatusMessage() {
         message.append(getStatusMessage(build));
         return this;
      }

      static String getStatusMessage(AbstractBuild r) {
         if(r.isBuilding()) {
            return "Starting...";
         }
         Result result = r.getResult();
         if(result == Result.SUCCESS) return "Success";
         if(result == Result.FAILURE) return "<b>FAILURE</b>";
         if(result == Result.ABORTED) return "ABORTED";
         if(result == Result.NOT_BUILT) return "Not built";
         if(result == Result.UNSTABLE) return "Unstable";
         return "Unknown";
      }

      public MessageBuilder append(String string) {
         message.append(string);
         return this;
      }

      public MessageBuilder append(Object string) {
         message.append(string.toString());
         return this;
      }

      private MessageBuilder startMessage() {
         message.append(build.getProject().getDisplayName());
         message.append(" - ");
         message.append(build.getDisplayName());
         message.append(" ");
         return this;
      }

      public MessageBuilder appendOpenLink() {
         String url = notifier.getJenkinsUrl() + build.getUrl();
         message.append(" (<a href='").append(url).append("'>Open</a>)");
         return this;
      }

      public MessageBuilder appendDuration() {
         message.append(" after ");
         message.append(build.getDurationString());
         return this;
      }

      public String toString() {
         return message.toString();
      }
   }
}
