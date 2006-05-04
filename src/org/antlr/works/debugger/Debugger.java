/*

[The "BSD licence"]
Copyright (c) 2005 Jean Bovet
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions
are met:

1. Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.
2. Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.
3. The name of the author may not be used to endorse or promote products
derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

*/

package org.antlr.works.debugger;

import edu.usfca.xj.appkit.frame.XJDialog;
import edu.usfca.xj.appkit.gview.GView;
import edu.usfca.xj.appkit.utils.XJAlert;
import edu.usfca.xj.foundation.notification.XJNotificationCenter;
import org.antlr.runtime.ClassicToken;
import org.antlr.runtime.Token;
import org.antlr.works.ate.syntax.misc.ATELine;
import org.antlr.works.components.grammar.CEditorGrammar;
import org.antlr.works.debugger.events.DBEvent;
import org.antlr.works.debugger.input.DBInputText;
import org.antlr.works.debugger.input.DBInputTextTokenInfo;
import org.antlr.works.debugger.local.DBLocal;
import org.antlr.works.debugger.panels.DBControlPanel;
import org.antlr.works.debugger.panels.DBInfoPanel;
import org.antlr.works.debugger.remote.DBRemoteConnectDialog;
import org.antlr.works.debugger.tivo.DBPlayer;
import org.antlr.works.debugger.tivo.DBPlayerContextInfo;
import org.antlr.works.debugger.tivo.DBRecorder;
import org.antlr.works.debugger.tree.DBASTModel;
import org.antlr.works.debugger.tree.DBASTPanel;
import org.antlr.works.debugger.tree.DBParseTreeModel;
import org.antlr.works.debugger.tree.DBParseTreePanel;
import org.antlr.works.editor.EditorConsole;
import org.antlr.works.editor.EditorMenu;
import org.antlr.works.editor.EditorProvider;
import org.antlr.works.editor.EditorTab;
import org.antlr.works.generate.DialogGenerate;
import org.antlr.works.grammar.EngineGrammar;
import org.antlr.works.menu.ContextualMenuFactory;
import org.antlr.works.prefs.AWPrefs;
import org.antlr.works.stats.Statistics;
import org.antlr.works.utils.StreamWatcherDelegate;
import org.antlr.works.utils.TextPane;
import org.antlr.works.utils.TextUtils;

import javax.swing.*;
import javax.swing.text.AttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyleContext;
import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Debugger extends EditorTab implements StreamWatcherDelegate {

    public static final String DEFAULT_LOCAL_ADDRESS = "localhost";

    public static final String NOTIF_DEBUG_STARTED = "NOTIF_DEBUG_STARTED";
    public static final String NOTIF_DEBUG_STOPPED = "NOTIF_DEBUG_STOPPED";

    public static final boolean BUILD_AND_DEBUG = true;
    public static final boolean DEBUG = false;

    protected JPanel panel;
    protected TextPane inputTextPane;
    protected TextPane outputTextPane;
    protected JTabbedPane treeTabbedPane;

    protected JPanel treeInfoCanvas;
    protected JComponent treePanel;

    protected JPanel ioCanvas;
    protected JComponent inputPanel;
    protected JComponent outputPanel;

    protected DBParseTreePanel parseTreePanel;
    protected DBParseTreeModel parseTreeModel;

    protected DBASTPanel astPanel;
    protected DBASTModel astModel;

    protected DBInfoPanel infoPanel;
    protected DBControlPanel controlPanel;

    protected CEditorGrammar editor;
    protected AttributeSet previousGrammarAttributeSet;
    protected int previousGrammarPosition;

    protected Set breakpoints;

    protected DBInputText inputText;
    protected DBLocal debuggerLocal;
    protected DBRecorder recorder;
    protected DBPlayer player;

    protected boolean running;
    protected JSplitPane ioSplitPane;
    protected JSplitPane ioTreeSplitPane;
    protected JSplitPane treeInfoPanelSplitPane;

    protected long dateOfModificationOnDisk = 0;

    public Debugger(CEditorGrammar editor) {
        this.editor = editor;
    }

    public void awake() {
        panel = new JPanel(new BorderLayout());

        ioCanvas = new JPanel(new BorderLayout());
        treeInfoCanvas = new JPanel(new BorderLayout());

        infoPanel = new DBInfoPanel();
        controlPanel = new DBControlPanel(this);
        treePanel = createTreePanel();

        inputPanel = createInputPanel();
        outputPanel = createOutputPanel();

        treeInfoPanelSplitPane = new JSplitPane();
        treeInfoPanelSplitPane.setBorder(null);
        treeInfoPanelSplitPane.setOrientation(JSplitPane.HORIZONTAL_SPLIT);
        treeInfoPanelSplitPane.setRightComponent(infoPanel);
        treeInfoPanelSplitPane.setContinuousLayout(true);
        treeInfoPanelSplitPane.setOneTouchExpandable(true);

        ioSplitPane = new JSplitPane();
        ioSplitPane.setBorder(null);
        ioSplitPane.setOrientation(JSplitPane.VERTICAL_SPLIT);
        ioSplitPane.setRightComponent(outputPanel);
        ioSplitPane.setContinuousLayout(true);
        ioSplitPane.setOneTouchExpandable(true);

        ioTreeSplitPane = new JSplitPane();
        ioTreeSplitPane.setBorder(null);
        ioTreeSplitPane.setOrientation(JSplitPane.HORIZONTAL_SPLIT);
        ioTreeSplitPane.setLeftComponent(ioCanvas);
        ioTreeSplitPane.setRightComponent(treeInfoCanvas);
        ioTreeSplitPane.setContinuousLayout(true);
        ioTreeSplitPane.setOneTouchExpandable(true);

        panel.add(controlPanel, BorderLayout.NORTH);
        panel.add(ioTreeSplitPane, BorderLayout.CENTER);

        inputText = new DBInputText(this, inputTextPane);
        debuggerLocal = new DBLocal(this);
        recorder = new DBRecorder(this);
        player = new DBPlayer(this, inputText);

        ioCanvas.add(inputPanel, BorderLayout.CENTER);
        treeInfoCanvas.add(treePanel, BorderLayout.CENTER);

        updateStatusInfo();
    }

    public void componentShouldLayout() {
        //treeInfoPanelSplitPane.setDividerLocation(0.6);
        ioTreeSplitPane.setDividerLocation(0.2);
        //ioSplitPane.setDividerLocation(0.2);

        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                astPanel.componentShouldLayout();
            }
        });
    }

    public void toggleInformationPanel() {
        if(isInfoPanelVisible()) {
            treeInfoPanelSplitPane.setLeftComponent(null);
            treeInfoCanvas.remove(0);
            treeInfoCanvas.add(treePanel);
        } else {
            treeInfoCanvas.remove(0);
            treeInfoCanvas.add(treeInfoPanelSplitPane);
            treeInfoPanelSplitPane.setLeftComponent(treePanel);
            treeInfoPanelSplitPane.setDividerLocation((int)(treeInfoCanvas.getWidth()*0.6));
        }
        treeInfoCanvas.revalidate();
    }

    public boolean isInfoPanelVisible() {
        return treeInfoCanvas.getComponent(0) == treeInfoPanelSplitPane;
    }

    public void toggleOutputPanel() {
        if(isOutputPanelVisible()) {
            ioSplitPane.setLeftComponent(null);
            ioCanvas.remove(0);
            ioCanvas.add(inputPanel);
        } else {
            ioCanvas.remove(0);
            ioCanvas.add(ioSplitPane);
            ioSplitPane.setLeftComponent(inputPanel);
            ioSplitPane.setDividerLocation((int)(ioCanvas.getWidth()*0.4));
        }
        ioCanvas.revalidate();
    }

    public boolean isOutputPanelVisible() {
        return ioCanvas.getComponent(0) == ioSplitPane;
    }

    public void selectConsoleTab() {
        editor.selectConsoleTab();
    }

    public DBRecorder getRecorder() {
        return recorder;
    }

    public DBPlayer getPlayer() {
        return player;
    }

    public Container getWindowComponent() {
        return editor.getWindowContainer();
    }

    public EditorConsole getConsole() {
        return editor.getConsole();
    }

    public EditorProvider getProvider() {
        return editor;
    }

    public void close() {
        debuggerStop(true);
        player.close();
    }

    public JComponent createInputPanel() {
        inputTextPane = new TextPane();
        inputTextPane.setBackground(Color.white);
        inputTextPane.setBorder(null);
        inputTextPane.setFont(new Font(AWPrefs.getEditorFont(), Font.PLAIN, AWPrefs.getEditorFontSize()));
        inputTextPane.setText("");
        inputTextPane.setEditable(false);

        TextUtils.createTabs(inputTextPane);

        JScrollPane textScrollPane = new JScrollPane(inputTextPane);
        textScrollPane.setWheelScrollingEnabled(true);

        return textScrollPane;
    }

    public JComponent createOutputPanel() {
        outputTextPane = new TextPane();
        outputTextPane.setBackground(Color.white);
        outputTextPane.setBorder(null);
        outputTextPane.setFont(new Font(AWPrefs.getEditorFont(), Font.PLAIN, AWPrefs.getEditorFontSize()));
        outputTextPane.setText("");
        outputTextPane.setEditable(false);

        TextUtils.createTabs(outputTextPane);

        JScrollPane textScrollPane = new JScrollPane(outputTextPane);
        textScrollPane.setWheelScrollingEnabled(true);

        return textScrollPane;
    }

    public JComponent createTreePanel() {
        parseTreeModel = new DBParseTreeModel(this);
        parseTreePanel = new DBParseTreePanel(this);
        parseTreePanel.setModel(parseTreeModel);

        astModel = new DBASTModel();
        astPanel = new DBASTPanel(this);
        astPanel.setModel(astModel);

        treeTabbedPane = new JTabbedPane();
        //treeTabbedPane.setTabPlacement(JTabbedPane.BOTTOM);
        treeTabbedPane.add("Parse Tree", parseTreePanel);
        treeTabbedPane.add("AST", astPanel);

        return treeTabbedPane;
    }

    public Container getContainer() {
        return panel;
    }

    public void updateStatusInfo() {
        controlPanel.updateStatusInfo();
    }

    public EngineGrammar getGrammar() {
        return editor.getEngineGrammar();
    }

    public boolean needsToGenerateGrammar() {
        return dateOfModificationOnDisk != editor.getDocument().getDateOfModificationOnDisk()
                || editor.getDocument().isDirty();
    }

    public void grammarGenerated() {
        editor.getDocument().performAutoSave();
        dateOfModificationOnDisk = editor.getDocument().getDateOfModificationOnDisk();
    }

    public void queryGrammarBreakpoints() {
        this.breakpoints = editor.breakpointManager.getBreakpoints();
    }

    public boolean isBreakpointAtLine(int line) {
        if(breakpoints == null)
            return false;
        else
            return breakpoints.contains(new Integer(line));
    }

    public boolean isBreakpointAtToken(Token token) {
        return inputText.isBreakpointAtToken(token);
    }

    public void selectToken(Token token, int line, int pos) {
        if(token != null) {
            /** If token is not null, ask the input text object the
             * line and character number.
             */

            DBInputTextTokenInfo info = inputText.getTokenInfoForToken(token);
            setGrammarPosition(info.line, info.charInLine);
        } else {
            /** If token is null, the line and pos will be provided as parameters */
            setGrammarPosition(line, pos);
        }

        inputText.selectToken(token);
        parseTreePanel.selectToken(token);
        astPanel.selectToken(token);
    }

    public int grammarIndex;

    public void setGrammarPosition(int line, int pos) {
        grammarIndex = computeAbsoluteGrammarIndex(line, pos);
        if(grammarIndex >= 0) {
            if(editor.getTextPane().hasFocus()) {
                // If the text pane will lose its focus,
                // delay the text selection otherwise
                // the selection will be hidden
                SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        editor.selectTextRange(grammarIndex, grammarIndex+1);
                    }
                });
            } else
                editor.selectTextRange(grammarIndex, grammarIndex+1);
        }
    }

    public void markLocationInGrammar(int index) {
        try {
            editor.setCaretPosition(index);
            storeGrammarAttributeSet(index);

            StyleContext sc = StyleContext.getDefaultStyleContext();
            AttributeSet attr = sc.addAttribute(SimpleAttributeSet.EMPTY, StyleConstants.Background, Color.red);
            editor.getTextPane().getStyledDocument().setCharacterAttributes(index, 1, attr, false);
        } catch(Exception e) {
            getConsole().print(e);
        }
    }

    public List getRules() {
        return editor.getRules();
    }

    public String getEventsAsString() {
        return infoPanel.getEventsAsString();
    }

    public void launchLocalDebugger(boolean buildAndDebug) {
        // If the grammar is dirty, build it anyway

        if(needsToGenerateGrammar())
            buildAndDebug = true;

        if(buildAndDebug || !debuggerLocal.isRequiredFilesExisting()) {
            DialogGenerate dialog = new DialogGenerate(getWindowComponent());
            dialog.setDebugOnly();
            if(dialog.runModal() == XJDialog.BUTTON_OK) {
                debuggerLocal.setOutputPath(dialog.getOutputPath());
                debuggerLocal.prepareAndLaunch(BUILD_AND_DEBUG);

                grammarGenerated();
            }
        } else {
            debuggerLocal.prepareAndLaunch(DEBUG);
        }
    }

    public void debuggerLocalDidRun(boolean builtAndDebug) {
        if(builtAndDebug)
            Statistics.shared().recordEvent(Statistics.EVENT_LOCAL_DEBUGGER_BUILD);
        else
            Statistics.shared().recordEvent(Statistics.EVENT_LOCAL_DEBUGGER);
        debuggerLaunch(DEFAULT_LOCAL_ADDRESS, AWPrefs.getDebugDefaultLocalPort());
    }

    public void launchRemoteDebugger() {
        Statistics.shared().recordEvent(Statistics.EVENT_REMOTE_DEBUGGER);
        DBRemoteConnectDialog dialog = new DBRemoteConnectDialog(getWindowComponent());
        if(dialog.runModal() == XJDialog.BUTTON_OK) {
            debuggerLaunch(dialog.getAddress(), dialog.getPort());
        }
    }

    public void debuggerLaunch(String address, int port) {
        if(!debuggerLaunchGrammar()) {
            XJAlert.display(editor.getWindowContainer(), "Error",
                    "Cannot launch the debugger.\nException while parsing grammar.");
            return;
        }

        queryGrammarBreakpoints();
        recorder.connect(address, port);
    }

    public void connectionSuccess() {
        // First set the flag to true before doing anything else
        // (don't send the notification before for example)
        running = true;

        XJNotificationCenter.defaultCenter().postNotification(this, NOTIF_DEBUG_STARTED);
        editor.selectDebuggerTab();

        editor.console.makeCurrent();

        editor.getTextPane().setEditable(false);
        editor.getTextPane().requestFocus(false);
        previousGrammarAttributeSet = null;
        player.resetPlayEvents(true);
    }

    public void connectionFailed() {
        XJAlert.display(editor.getWindowContainer(), "Connection Error",
                "Cannot launch the debugger.\nTime-out waiting to connect to the remote parser.");
    }

    public void connectionCancelled() {
    }

    public boolean debuggerLaunchGrammar() {
        try {
            getGrammar().analyze();
        } catch (Exception e) {
            editor.getConsole().print(e);
            return false;
        }
        return true;
    }

    public void debuggerStop(boolean force) {
        if(recorder.getStatus() == DBRecorder.STATUS_STOPPING) {
            if(force || XJAlert.displayAlertYESNO(editor.getWindowContainer(), "Stopping", "The debugger is currently stopping. Do you want to force stop it ?") == XJAlert.YES) {
                debuggerLocal.forceStop();
                recorder.forceStop();
            }
        } else
            recorder.stop();
    }

    public boolean isRunning() {
        return running;
    }

    public void resetGUI() {
        infoPanel.clear();
        parseTreePanel.clear();
        astPanel.clear();
    }

    public void storeGrammarAttributeSet(int index) {
        previousGrammarPosition = index;
        previousGrammarAttributeSet = editor.getTextPane().getStyledDocument().getCharacterElement(index+1).getAttributes();
    }

    public void restorePreviousGrammarAttributeSet() {
        if(previousGrammarAttributeSet != null) {
            editor.getTextPane().getStyledDocument().setCharacterAttributes(previousGrammarPosition, 1, previousGrammarAttributeSet, true);
            previousGrammarAttributeSet = null;
        }
    }

    public int computeAbsoluteGrammarIndex(int lineIndex, int pos) {
        List lines = editor.getLines();
        if(lineIndex-1<0 || lineIndex-1 >= lines.size())
            return -1;

        ATELine line = (ATELine)lines.get(lineIndex-1);
        String t = editor.getText();

        // ANTLR gives a position using a tab size of 8. I have to
        // convert this to the current editor tab size
        // @todo if ANTLR changes the tab size, adjust here
        int antlr_tab = 8;
        int antlr_pos = 0;
        int c = 0;
        while(antlr_pos<pos) {
            if(t.charAt(line.position+c) == '\t') {
                antlr_pos = ((antlr_pos/antlr_tab)+1)*antlr_tab;
            } else {
                antlr_pos++;
            }

            c++;
        }
        return line.position+(c-1);
    }

    public void addEvent(DBEvent event, DBPlayerContextInfo info) {
        infoPanel.addEvent(event, info);
        infoPanel.selectLastInfoTableItem();
    }

    public void playEvents(List events, boolean reset) {
        player.playEvents(events, reset);
    }

    public void pushRule(String ruleName, int line, int pos) {
        infoPanel.pushRule(ruleName);
        parseTreeModel.pushRule(ruleName, line, pos);
        astModel.pushRule(ruleName);
        astPanel.selectLastRule();
    }

    public void popRule(String ruleName) {
        infoPanel.popRule();
        parseTreeModel.popRule();
        astModel.popRule();
    }

    public void addToken(Token token) {
        parseTreeModel.addToken(token);
    }

    public void addException(Exception e) {
        parseTreeModel.addException(e);
    }

    public void beginBacktrack(int level) {
        parseTreeModel.beginBacktrack(level);
    }

    public void endBacktrack(int level, boolean success) {
        parseTreeModel.endBacktrack(level, success);
    }

    public void astNilNode(int id) {
        astModel.nilNode(id);
        astPanel.selectLastRootNode();
    }

    public void astCreateNode(int id, Token token) {
        astModel.createNode(id, token);
    }

    public void astCreateNode(int id, String text, int type) {
        astModel.createNode(id, new ClassicToken(type, text));
    }

    public void astBecomeRoot(int newRootID, int oldRootID) {
        astModel.becomeRoot(newRootID, oldRootID);
    }

    public void astAddChild(int rootID, int childID) {
        astModel.addChild(rootID, childID);
    }

    public void astSetTokenBoundaries(int id, int startIndex, int stopIndex) {
        /** Currently ignored */
    }

    public void recorderStatusDidChange() {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                updateStatusInfo();
            }
        });
    }

    public void recorderDidStop() {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                restorePreviousGrammarAttributeSet();
                editor.getTextPane().setEditable(true);
                inputText.stop();
                running = false;
                editor.refreshMainMenuBar();
                XJNotificationCenter.defaultCenter().postNotification(this, NOTIF_DEBUG_STOPPED);
            }
        });
    }

    public void streamWatcherDidStarted() {
        outputTextPane.setText("");
    }

    public void streamWatcherDidReceiveString(String string) {
        outputTextPane.setText(outputTextPane.getText()+string);
    }

    public void streamWatcherException(Exception e) {
        editor.getConsole().print(e);
    }

    public boolean canExportToBitmap() {
        return true;
    }

    public boolean canExportToEPS() {
        return true;
    }

    public GView getExportableGView() {
        if(treeTabbedPane.getSelectedComponent() == parseTreePanel)
            return parseTreePanel.getGraphView();
        else
            return astPanel.getGraphView();
    }

    public String getTabName() {
        return "Debugger";
    }

    public Component getTabComponent() {
        return getContainer();
    }

    public JPopupMenu treeGetContextualMenu() {
        ContextualMenuFactory factory = new ContextualMenuFactory(editor.editorMenu);
        factory.addItem(EditorMenu.MI_EXPORT_AS_EPS);
        factory.addItem(EditorMenu.MI_EXPORT_AS_IMAGE);
        return factory.menu;
    }

    public static final String KEY_SPLITPANE_A = "KEY_SPLITPANE_A";
    public static final String KEY_SPLITPANE_B = "KEY_SPLITPANE_B";
    public static final String KEY_SPLITPANE_C = "KEY_SPLITPANE_C";

    public void setPersistentData(Map data) {
        if(data == null)
            return;

        Integer i = (Integer)data.get(KEY_SPLITPANE_A);
        if(i != null)
            ioSplitPane.setDividerLocation(i.intValue());

        i = (Integer)data.get(KEY_SPLITPANE_B);
        if(i != null)
            ioTreeSplitPane.setDividerLocation(i.intValue());

        i = (Integer)data.get(KEY_SPLITPANE_C);
        if(i != null)
            treeInfoPanelSplitPane.setDividerLocation(i.intValue());
    }

    public Map getPersistentData() {
        Map data = new HashMap();
        data.put(KEY_SPLITPANE_A, new Integer(ioSplitPane.getDividerLocation()));
        data.put(KEY_SPLITPANE_B, new Integer(ioTreeSplitPane.getDividerLocation()));
        data.put(KEY_SPLITPANE_C, new Integer(treeInfoPanelSplitPane.getDividerLocation()));
        return data;
    }


}
