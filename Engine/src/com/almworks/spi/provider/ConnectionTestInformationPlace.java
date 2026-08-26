package com.almworks.spi.provider;

import com.almworks.util.collections.Modifiable;
import com.almworks.util.collections.SimpleModifiable;
import com.almworks.util.components.ALabelWithExplanation;
import com.almworks.util.threads.Threads;
import com.almworks.util.ui.swing.AwtUtil;
import com.jgoodies.forms.layout.CellConstraints;
import com.jgoodies.forms.layout.FormLayout;
import com.jgoodies.forms.layout.RowSpec;
import org.almworks.util.Collections15;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;

public class ConnectionTestInformationPlace {
  private static final String NL = System.getProperty("line.separator", "\n");

  private final JPanel myMessagesPlace;
  private final JScrollPane myScrollPane;

  /**
   * All messages ever added since the last {@link #clearMessages()}, in the order
   * they were added. The visible labels may be scrolled out of view or elided, so
   * the text is kept here as well, see {@link #getAllMessagesText()}.
   */
  private final List<MessageHandle> myHandles = Collections15.arrayList();

  /** Fires when messages are added or cleared, so that actions can update themselves. */
  private final SimpleModifiable myModifiable = new SimpleModifiable();

  public ConnectionTestInformationPlace() {
    myMessagesPlace = new JPanel(new FormLayout("left:max(16px;min),4dlu,fill:pref"));
    final JPanel marginPanel = new JPanel(new BorderLayout());
    marginPanel.setBorder(new EmptyBorder(0, 5, 0, 5));
    marginPanel.add(myMessagesPlace, BorderLayout.CENTER);
    myScrollPane = new JScrollPane(marginPanel);
  }

  public JComponent getComponent() {
    return myScrollPane;
  }

  public void clearMessages() {
    Threads.assertAWTThread();
    myHandles.clear();
    myMessagesPlace.removeAll();
    myMessagesPlace.setBorder(AwtUtil.EMPTY_BORDER);
    final FormLayout layout = ((FormLayout)myMessagesPlace.getLayout());
    for(int row = layout.getRowCount(); row > 0; row--) {
      layout.removeRow(row);
    }
    myModifiable.fireChanged();
  }

  /** @return a modifiable that fires whenever the set of messages changes. */
  public Modifiable getModifiable() {
    return myModifiable;
  }

  public MessageHandle addMessage(@Nullable Icon icon, String shortMessage, @Nullable String longMessage) {
    Threads.assertAWTThread();
    final int row = addNewRowAndGetItsIndex();
    final MessageHandle mh =
      new MessageHandle(addIconComponent(icon, row), addMessageComponent(shortMessage, longMessage, row),
        shortMessage, longMessage);
    myHandles.add(mh);
    revalidateScrollPane();
    myModifiable.fireChanged();
    return mh;
  }

  public boolean hasMessages() {
    return !myHandles.isEmpty();
  }

  /**
   * @return the full text of every message currently on display, including the
   * ones scrolled out of view and the long explanations behind the "question
   * mark" links. Returns an empty string when there is nothing to report.
   */
  public String getAllMessagesText() {
    final StringBuilder sb = new StringBuilder();
    for(final MessageHandle mh : myHandles) {
      final String shortMessage = mh.getShortMessage();
      if(shortMessage != null && !shortMessage.isEmpty()) {
        if(sb.length() > 0) {
          sb.append(NL);
        }
        sb.append(shortMessage);
      }
      final String longMessage = mh.getLongMessage();
      if(longMessage != null && !longMessage.isEmpty()) {
        if(sb.length() > 0) {
          sb.append(NL);
        }
        sb.append(indent(longMessage));
      }
    }
    return sb.toString();
  }

  private static String indent(String text) {
    final StringBuilder sb = new StringBuilder();
    for(final String line : text.split("\r\n|\r|\n", -1)) {
      if(sb.length() > 0) {
        sb.append(NL);
      }
      sb.append("    ").append(line);
    }
    return sb.toString();
  }

  private int addNewRowAndGetItsIndex() {
    final FormLayout layout = (FormLayout) myMessagesPlace.getLayout();
    layout.appendRow(new RowSpec("3dlu"));
    layout.appendRow(new RowSpec("fill:pref"));
    return layout.getRowCount();
  }

  private JLabel addIconComponent(@Nullable Icon icon, int row) {
    final JLabel label = new JLabel();
    if(icon != null) {
      label.setIcon(icon);
    }
    myMessagesPlace.add(label, new CellConstraints(1, row));
    return label;
  }

  private ALabelWithExplanation addMessageComponent(String shortMessage, String longMessage, int row) {
    final ALabelWithExplanation label = createMessageComponent(shortMessage, longMessage);
    myMessagesPlace.add(label, new CellConstraints(3, row));
    return label;
  }

  private ALabelWithExplanation createMessageComponent(String shortMessage, String longMessage) {
    final ALabelWithExplanation label = new ALabelWithExplanation();
    label.setTextAndExplanation(shortMessage, longMessage);
    return label;
  }

  private void revalidateScrollPane() {
    myScrollPane.revalidate();
  }

  public static class MessageHandle {
    private final JLabel myIcon;
    private final ALabelWithExplanation myLabel;

    private String myShortMessage;
    private String myLongMessage;

    private MessageHandle(JLabel icon, ALabelWithExplanation label,
      @Nullable String shortMessage, @Nullable String longMessage)
    {
      myIcon = icon;
      myLabel = label;
      myShortMessage = shortMessage;
      myLongMessage = longMessage;
    }

    public void setIcon(@Nullable Icon icon) {
      myIcon.setIcon(icon);
    }

    public void setMessage(String message) {
      myShortMessage = message;
      myLabel.setText(message);
    }

    public void setExplanation(String explanation) {
      myLongMessage = explanation;
      myLabel.setExplanation(explanation);
    }

    @Nullable
    public String getShortMessage() {
      return myShortMessage;
    }

    @Nullable
    public String getLongMessage() {
      return myLongMessage;
    }
  }
}
