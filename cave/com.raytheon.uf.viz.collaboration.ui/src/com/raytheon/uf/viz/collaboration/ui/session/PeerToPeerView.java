package com.raytheon.uf.viz.collaboration.ui.session;

/**
 * This software was developed and / or modified by Raytheon Company,
 * pursuant to Contract DG133W-05-CQ-1067 with the US Government.
 * 
 * U.S. EXPORT CONTROLLED TECHNICAL DATA
 * This software product contains export-restricted data whose
 * export/transfer/disclosure is restricted by U.S. law. Dissemination
 * to non-U.S. persons whether in the United States or abroad requires
 * an export license or other authorization.
 * 
 * Contractor Name:        Raytheon Company
 * Contractor Address:     6825 Pine Street, Suite 340
 *                         Mail Stop B8
 *                         Omaha, NE 68106
 *                         402.291.0100
 * 
 * See the AWIPS II Master Rights File ("Master Rights File.pdf") for
 * further licensing information.
 **/

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.plaf.synth.ColorType;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IContributionItem;
import org.eclipse.jface.action.ToolBarManager;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.ColorDialog;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.jivesoftware.smack.packet.Presence.Type;

import com.google.common.eventbus.Subscribe;
import com.raytheon.uf.common.status.IUFStatusHandler;
import com.raytheon.uf.common.status.UFStatus;
import com.raytheon.uf.common.status.UFStatus.Priority;
import com.raytheon.uf.viz.collaboration.comm.identity.CollaborationException;
import com.raytheon.uf.viz.collaboration.comm.identity.IMessage;
import com.raytheon.uf.viz.collaboration.comm.identity.IPeerToPeer;
import com.raytheon.uf.viz.collaboration.comm.identity.user.IUser;
import com.raytheon.uf.viz.collaboration.comm.provider.connection.CollaborationConnection;
import com.raytheon.uf.viz.collaboration.comm.provider.user.RosterItem;
import com.raytheon.uf.viz.collaboration.comm.provider.user.UserId;
import com.raytheon.uf.viz.collaboration.comm.provider.user.VenueParticipant;
import com.raytheon.uf.viz.collaboration.ui.Activator;
import com.raytheon.uf.viz.collaboration.ui.UserColorConfigManager;
import com.raytheon.uf.viz.collaboration.ui.UserColorInformation.UserColor;
import com.raytheon.uf.viz.collaboration.ui.actions.PrintLogActionContributionItem;
import com.raytheon.uf.viz.collaboration.ui.notifier.NotifierTask;
import com.raytheon.uf.viz.collaboration.ui.notifier.NotifierTools;
import com.raytheon.uf.viz.core.icon.IconUtil;
import com.raytheon.uf.viz.core.sounds.SoundUtil;

/**
 * UI display for one-on-one chat sessions
 * 
 * <pre>
 * 
 * SOFTWARE HISTORY
 * 
 * Date         Ticket#    Engineer    Description
 * ------------ ---------- ----------- --------------------------
 * Mar 1, 2012            rferrel     Initial creation
 * Jan 30, 2014 2698       bclement    added getDisplayName
 * Feb 13, 2014 2751       bclement   made parent generic
 * Feb 28, 2014 2632       mpduff      Override appendMessage for notifiers
 * Jun 17, 2014 3078       bclement    changed peer type to IUser
 * Nov 14, 2014 3709       mapeters    support foregound/background color 
 *                                     settings for each user
 * 
 * </pre>
 * 
 * @author rferrel
 * @version 1.0
 */
public class PeerToPeerView extends AbstractSessionView<IUser> implements
        IPrintableView {
    private static final transient IUFStatusHandler statusHandler = UFStatus
            .getHandler(PeerToPeerView.class);

    private static final String PEER_TO_PEER_IMAGE_NAME = "chats.gif";

    public static final String ID = "com.raytheon.uf.viz.collaboration.PeerToPeerView";

    private static final Color DEFAULT_USER_FOREGROUND_COLOR = Display
            .getCurrent().getSystemColor(SWT.COLOR_DARK_BLUE);

    private static final Color DEFAULT_PEER_FOREGROUND_COLOR = Display
            .getCurrent().getSystemColor(SWT.COLOR_RED);

    private static final Color BLACK = Display.getCurrent().getSystemColor(
            SWT.COLOR_BLACK);

    private Map<RGB, Color> rgbToColor = new HashMap<>();

    private IUser peer;

    private boolean online = true;

    public PeerToPeerView() {
        super();
        CollaborationConnection.getConnection().registerEventHandler(this);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.raytheon.uf.viz.collaboration.ui.session.AbstractSessionView#dispose
     * ()
     */
    @Override
    public void dispose() {
        CollaborationConnection conn = CollaborationConnection.getConnection();
        if (conn != null) {
            conn.unregisterEventHandler(this);
        }
        super.dispose();

        for (Color color : rgbToColor.values()) {
            color.dispose();
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.raytheon.uf.viz.collaboration.ui.session.AbstractSessionView#
     * populateSashForm(org.eclipse.swt.custom.SashForm)
     */
    @Override
    protected void populateSashForm(SashForm sashForm) {
        super.populateSashForm(sashForm);
        sashForm.setWeights(new int[] { 20, 5 });
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.raytheon.uf.viz.collaboration.ui.session.AbstractSessionView#
     * appendMessage(com.raytheon.uf.viz.collaboration.comm.identity.IMessage)
     */
    @Override
    public void appendMessage(IMessage message) {
        // Check for message notifiers
        NotifierTask task = NotifierTools.getNotifierTask(message.getFrom()
                .getName());
        if (task != null && task.containsSendMessage()) {
            if (task.isSoundValid()) {
                SoundUtil.playSound(task.getSoundFilePath());
                NotifierTools.taskExecuted(task);
            }
        }

        super.appendMessage(message);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.raytheon.uf.viz.collaboration.ui.session.AbstractSessionView#
     * setMessageLabel(org.eclipse.swt.widgets.Label)
     */
    @Override
    protected void setMessageLabel(Composite comp) {
        // no message needed as there is no subject and we know that it is
        // private based on the fact that there are no participants
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.raytheon.uf.viz.collaboration.ui.session.AbstractSessionView#sendMessage
     * ()
     */
    @Override
    public void sendMessage() {
        String message = getComposedMessage();
        if (message.length() > 0) {
            try {
                CollaborationConnection connection = CollaborationConnection
                        .getConnection();
                appendMessage(connection.getUser(), System.currentTimeMillis(),
                        message, null);
                if (online) {
                    IPeerToPeer p2p = (IPeerToPeer) connection
                            .getPeerToPeerSession();
                    p2p.sendPeerToPeer(peer, message);
                } else {
                    StringBuilder builder = new StringBuilder();
                    builder.append("Unable to send message. User is not online.");
                    sendErrorMessage(builder);
                }
            } catch (CollaborationException e) {
                statusHandler.handle(Priority.PROBLEM,
                        "Unable to send message to " + peer.getName(), e);
            }
        }
    }

    @Override
    protected void styleAndAppendText(StringBuilder sb, int offset,
            String name, IUser userId, String subject, List<StyleRange> ranges) {
        CollaborationConnection connection = CollaborationConnection
                .getConnection();
        if (connection == null) {
            return;
        }
        Color color = null;
        if (userId == null) {
            color = BLACK;
        } else if (!userId.equals(connection.getUser())) {
            color = DEFAULT_PEER_FOREGROUND_COLOR;
        } else {
            color = DEFAULT_USER_FOREGROUND_COLOR;
        }
        styleAndAppendText(sb, offset, name, userId, ranges, color);
    };

    @Override
    public void styleAndAppendText(StringBuilder sb, int offset, String name,
            IUser userId, List<StyleRange> ranges, Color color) {
        Color fgColor = color;
        Color bgColor = null;

        if (userId != null) {
            // get user colors from config manager
            UserColor userColor = UserColorConfigManager.getColorForUser(userId
                    .getName());
            if (userColor != null) {
                fgColor = getColorFromRGB(userColor
                        .getColor(ColorType.FOREGROUND));
                bgColor = getColorFromRGB(userColor
                        .getColor(ColorType.BACKGROUND));
            }
        }

        StyleRange range = new StyleRange(messagesText.getCharCount(),
                sb.length(), fgColor, null, SWT.NORMAL);
        ranges.add(range);
        range = new StyleRange(messagesText.getCharCount() + offset,
                (userId != null ? name.length() + 1 : sb.length() - offset),
                fgColor, null, SWT.BOLD);
        ranges.add(range);
        messagesText.append(sb.toString());
        
        for (StyleRange newRange : ranges) {
            messagesText.setStyleRange(newRange);
        }
        
        int lineNumber = messagesText.getLineCount() - 1;
        messagesText.setLineBackground(lineNumber, 1, bgColor);
        messagesText.setTopIndex(lineNumber);
    }

    /**
     * Get corresponding Color from map using RGB
     * 
     * @param rgb
     * @return
     */
    private Color getColorFromRGB(RGB rgb) {
        Color color = rgbToColor.get(rgb);
        if (color == null) {
            color = new Color(Display.getCurrent(), rgb);
            rgbToColor.put(rgb, color);
        }
        return color;
    }

    @Override
    protected String getSessionImageName() {
        return PEER_TO_PEER_IMAGE_NAME;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.raytheon.uf.viz.collaboration.ui.session.AbstractSessionView#
     * getSessionName()
     */
    @Override
    protected String getSessionName() {
        if (peer == null) {
            return getViewSite().getSecondaryId();
        } else {
            return getDisplayName(peer);
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.raytheon.uf.viz.collaboration.ui.session.AbstractSessionView#
     * getMessageArchive()
     */
    @Override
    protected SessionMsgArchive createMessageArchive() {
        UserId me = CollaborationConnection.getConnection().getUser();
        return new SessionMsgArchive(me.getHost(), me.getName(), peer.getName());
    }

    public void setPeer(IUser peer) {
        this.peer = peer;
        setPartName(getSessionName());
        initMessageArchive();
    }

    public IUser getPeer() {
        return peer;
    }

    @Subscribe
    public void handleModifiedPresence(RosterItem entry) {
        UserId id = entry.getId();
        if (id.equals(peer)) {
            if (entry.getPresence().getType() == Type.unavailable) {
                online = false;
            } else {
                online = true;
            }
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.raytheon.uf.viz.collaboration.ui.session.AbstractSessionView#
     * initComponents(org.eclipse.swt.widgets.Composite)
     */
    @Override
    protected void initComponents(Composite parent) {
        super.initComponents(parent);

        // unfortunately this code cannot be a part of createToolbarButton
        // because I cannot instantiate the ACI until after the messagesText
        // widget is instantiated which happens in initComponents
        IContributionItem printAction = new PrintLogActionContributionItem(this);
        ToolBarManager mgr = (ToolBarManager) getViewSite().getActionBars()
                .getToolBarManager();
        mgr.insert(mgr.getSize() - 1, printAction);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.raytheon.uf.viz.collaboration.ui.session.IPrintableView#getStyledText
     * ()
     */
    @Override
    public StyledText getStyledText() {
        return messagesText;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.raytheon.uf.viz.collaboration.ui.session.IPrintableView#getHeaderText
     * ()
     */
    @Override
    public String getHeaderText() {
        DateFormat dateFormatter = new SimpleDateFormat("dd MMM yyyy");
        return "Conversation session with user " + getSessionName()
                + ", Date: "
                + dateFormatter.format(msgArchive.getCreationTime());
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.raytheon.uf.viz.collaboration.ui.session.AbstractSessionView#
     * getDisplayName
     * (com.raytheon.uf.viz.collaboration.comm.provider.user.UserId)
     */
    @Override
    protected String getDisplayName(IUser user) {
        if (user instanceof UserId) {
            return CollaborationConnection.getConnection().getContactsManager()
                    .getDisplayName((UserId) user);
        } else if (user instanceof VenueParticipant) {
            VenueParticipant participant = (VenueParticipant) user;
            return participant.getHandle() + " in " + participant.getRoom();
        } else {
            return peer.getFQName();
        }
    }

    /**
     * add right-click menu options for changing foreground/background colors
     * for each user
     */
    public void addChangeUserColorActions() {
        String myName = CollaborationConnection.getConnection().getUser()
                .getName();
        String peerName = peer.getName();
        messagesTextMenuMgr.add(new ChangeUserColorAction(ColorType.BACKGROUND,
                myName, true));
        messagesTextMenuMgr.add(new ChangeUserColorAction(ColorType.FOREGROUND,
                myName, true));
        messagesTextMenuMgr.add(new ChangeUserColorAction(ColorType.BACKGROUND,
                peerName, false));
        messagesTextMenuMgr.add(new ChangeUserColorAction(ColorType.FOREGROUND,
                peerName, false));
    }

    /*
     * action for changing foreground/background color for a given user
     */
    private class ChangeUserColorAction extends Action {

        ColorType type;

        String user;

        boolean me;

        public ChangeUserColorAction(ColorType type, String user, boolean me) {
            super("Change " + (me ? "Your " : (user + "'s ")) + type.toString()
                    + " Color...", IconUtil.getImageDescriptor(Activator
                    .getDefault().getBundle(), "change_color.gif"));
            this.type = type;
            this.user = user;
            this.me = me;
        }

        @Override
        public void run() {
            ColorDialog dialog = new ColorDialog(Display.getCurrent()
                    .getActiveShell());
            RGB defaultForeground = null;
            UserColor userColor = UserColorConfigManager.getColorForUser(user);
            if (userColor != null) {
                dialog.setRGB(userColor.getColor(type));
            } else {
                defaultForeground = me ? DEFAULT_USER_FOREGROUND_COLOR.getRGB()
                        : DEFAULT_PEER_FOREGROUND_COLOR.getRGB();
                if (type == ColorType.FOREGROUND) {
                    /*
                     * set the dialog to display default foreground color as
                     * currently selected
                     */
                    dialog.setRGB(defaultForeground);
                }
            }
            RGB rgb = dialog.open();
            if (rgb != null) {
                UserColorConfigManager.setColorForUser(user, type, rgb,
                        defaultForeground);
            }
        }
    }
}
