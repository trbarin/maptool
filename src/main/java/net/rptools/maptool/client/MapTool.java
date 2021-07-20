/*
 * This software Copyright by the RPTools.net development team, and
 * licensed under the Affero GPL Version 3 or, at your option, any later
 * version.
 *
 * MapTool Source Code is distributed in the hope that it will be
 * useful, but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * You should have received a copy of the GNU Affero General Public
 * License * along with this source Code.  If not, please visit
 * <http://www.gnu.org/licenses/> and specifically the Affero license
 * text at <http://www.gnu.org/licenses/agpl.html>.
 */
package net.rptools.maptool.client;

import static net.rptools.maptool.model.player.PlayerDatabaseFactory.PlayerDatabaseType.PERSONAL_SERVER;

import com.jidesoft.plaf.LookAndFeelFactory;
import com.jidesoft.plaf.UIDefaultsLookup;
import com.jidesoft.plaf.basic.ThemePainter;
import de.muntjak.tinylookandfeel.Theme;
import io.sentry.Sentry;
import io.sentry.SentryClient;
import io.sentry.SentryClientFactory;
import io.sentry.event.BreadcrumbBuilder;
import io.sentry.event.UserBuilder;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.Transparency;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.text.MessageFormat;
import java.util.*;
import javax.imageio.ImageIO;
import javax.imageio.spi.IIORegistry;
import javax.swing.*;
import javax.swing.plaf.FontUIResource;
import net.rptools.clientserver.hessian.client.ClientConnection;
import net.rptools.lib.BackupManager;
import net.rptools.lib.DebugStream;
import net.rptools.lib.EventDispatcher;
import net.rptools.lib.FileUtil;
import net.rptools.lib.TaskBarFlasher;
import net.rptools.lib.image.ThumbnailManager;
import net.rptools.lib.net.RPTURLStreamHandlerFactory;
import net.rptools.lib.sound.SoundManager;
import net.rptools.lib.swing.SwingUtil;
import net.rptools.maptool.client.functions.UserDefinedMacroFunctions;
import net.rptools.maptool.client.swing.MapToolEventQueue;
import net.rptools.maptool.client.swing.NoteFrame;
import net.rptools.maptool.client.swing.SplashScreen;
import net.rptools.maptool.client.ui.AppMenuBar;
import net.rptools.maptool.client.ui.ConnectionStatusPanel;
import net.rptools.maptool.client.ui.MapToolFrame;
import net.rptools.maptool.client.ui.OSXAdapter;
import net.rptools.maptool.client.ui.StartServerDialogPreferences;
import net.rptools.maptool.client.ui.logger.LogConsoleFrame;
import net.rptools.maptool.client.ui.zone.PlayerView;
import net.rptools.maptool.client.ui.zone.ZoneRenderer;
import net.rptools.maptool.client.ui.zone.ZoneRendererFactory;
import net.rptools.maptool.language.I18N;
import net.rptools.maptool.model.AssetManager;
import net.rptools.maptool.model.Campaign;
import net.rptools.maptool.model.CampaignFactory;
import net.rptools.maptool.model.GUID;
import net.rptools.maptool.model.ObservableList;
import net.rptools.maptool.model.player.LocalPlayer;
import net.rptools.maptool.model.player.LocalPlayerDatabase;
import net.rptools.maptool.model.player.Player;
import net.rptools.maptool.model.TextMessage;
import net.rptools.maptool.model.Zone;
import net.rptools.maptool.model.ZoneFactory;
import net.rptools.maptool.model.player.PlayerDatabase;
import net.rptools.maptool.model.player.PlayerDatabaseFactory;
import net.rptools.maptool.model.player.PlayerDatabaseFactory.PlayerDatabaseType;
import net.rptools.maptool.protocol.syrinscape.SyrinscapeURLStreamHandler;
import net.rptools.maptool.server.MapToolServer;
import net.rptools.maptool.server.ServerCommand;
import net.rptools.maptool.server.ServerConfig;
import net.rptools.maptool.server.ServerPolicy;
import net.rptools.maptool.transfer.AssetTransferManager;
import net.rptools.maptool.util.MessageUtil;
import net.rptools.maptool.util.StringUtil;
import net.rptools.maptool.util.UPnPUtil;
import net.rptools.maptool.util.UserJvmOptions;
import net.rptools.maptool.webapi.MTWebAppServer;
import net.rptools.maptool.webendpoint.WebEndPoint;
import net.rptools.parser.ParserException;
import net.tsc.servicediscovery.ServiceAnnouncer;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.appender.FileAppender;
import org.apache.logging.log4j.core.appender.RollingFileAppender;
import org.apache.logging.log4j.core.config.Configurator;

/** */
public class MapTool {

  private static final Logger log = LogManager.getLogger(MapTool.class);

  private static SentryClient sentry;

  /**
   * Specifies the properties file that holds sound information. Only two sounds currently:
   * <b>Dink</b> and <b>Clink</b>.
   */
  private static final String SOUND_PROPERTIES = "net/rptools/maptool/client/sounds.properties";

  public static final String SND_INVALID_OPERATION = "invalidOperation";

  private static String clientId = AppUtil.readClientId();

  public enum ZoneEvent {
    Added,
    Removed,
    Activated,
    Deactivated
  }

  public enum PreferencesEvent {
    Changed
  }

  // Jamz: This sets the thumbnail size that is cached for imageThumbs
  // Set it to 500 (from 100) for now to support larger asset window previews
  // TODO: Add preferences option as well as add auto-purge after x days preferences
  private static final Dimension THUMBNAIL_SIZE =
      new Dimension(AppPreferences.getThumbnailSize(), AppPreferences.getThumbnailSize());

  private static ThumbnailManager thumbnailManager;
  private static String version = "DEVELOPMENT";
  private static String vendor = "RPTools!"; // Default, will get from JAR Manifest during normal
  // runtime

  private static Campaign campaign;

  private static ObservableList<Player> playerList;
  private static ObservableList<TextMessage> messageList;
  private static LocalPlayer player;

  private static ClientConnection conn;
  private static ClientMethodHandler handler;
  private static JMenuBar menuBar;
  private static MapToolFrame clientFrame;
  private static NoteFrame profilingNoteFrame;
  private static LogConsoleFrame logConsoleFrame;
  private static MapToolServer server;
  private static ServerCommand serverCommand;
  private static ServerPolicy serverPolicy;

  private static BackupManager backupManager;
  private static AssetTransferManager assetTransferManager;
  private static ServiceAnnouncer announcer;
  private static AutoSaveManager autoSaveManager;
  private static TaskBarFlasher taskbarFlasher;
  private static EventDispatcher eventDispatcher;
  private static MapToolLineParser parser = new MapToolLineParser();
  private static String lastWhisperer;

  private static final MTWebAppServer webAppServer = new MTWebAppServer();

  // Jamz: To support new command line parameters for multi-monitor support & enhanced PrintStream
  private static boolean debug = false;
  private static int graphicsMonitor = -1;
  private static boolean useFullScreen = false;
  private static int windowWidth = -1;
  private static int windowHeight = -1;
  private static int windowX = -1;
  private static int windowY = -1;
  private static String loadCampaignOnStartPath = "";

  public static Dimension getThumbnailSize() {
    return THUMBNAIL_SIZE;
  }

  /**
   * This method looks up the message key in the properties file and returns the resultant text with
   * the detail message from the <code>Throwable</code> appended to the end.
   *
   * @param msgKey the string to use when calling {@link I18N#getText(String)}
   * @param t the exception to be processed
   * @return the <code>String</code> result
   */
  public static String generateMessage(String msgKey, Throwable t) {
    String msg;
    if (t == null) {
      msg = I18N.getText(msgKey);
    } else if (msgKey == null) {
      msg = t.toString();
    } else {
      msg = I18N.getText(msgKey) + "<br/>" + t.toString();
    }
    return msg;
  }

  /**
   * This method is the base method for putting a dialog box up on the screen that might be an
   * error, a warning, or just an information message. Do not use this method if the desired result
   * is a simple confirmation box (use {@link #confirm(String, Object...)} instead).
   *
   * @param message the key in the properties file to put in the body of the dialog (formatted using
   *     <code>params</code>)
   * @param titleKey the key in the properties file to use when creating the title of the dialog
   *     window (formatted using <code>params</code>)
   * @param messageType JOptionPane.{ERROR|WARNING|INFORMATION}_MESSAGE
   * @param params optional parameters to use when formatting the data from the properties file
   */
  public static void showMessage(
      String message, String titleKey, int messageType, Object... params) {
    String title = I18N.getText(titleKey, params);
    JOptionPane.showMessageDialog(
        clientFrame, "<html>" + I18N.getText(message, params), title, messageType);
  }

  /**
   * Same as {@link #showMessage(String, String, int, Object...)} except that <code>messages</code>
   * is stored into a JList and that component is then used as the content of the dialog box. This
   * allows multiple strings to be displayed in a manner consistent with other message dialogs.
   *
   * @param messages the Objects (normally strings) to put in the body of the dialog; no properties
   *     file lookup is performed!
   * @param titleKey the key in the properties file to use when creating the title of the dialog
   *     window (formatted using <code>params</code>)
   * @param messageType one of <code>JOptionPane.ERROR_MESSAGE</code>, <code>
   *                    JOptionPane.WARNING_MESSAGE</code>, <code>JOptionPane.INFORMATION_MESSAGE
   *                    </code>
   * @param params optional parameters to use when formatting the title text from the properties
   *     file
   */
  public static void showMessage(
      Object[] messages, String titleKey, int messageType, Object... params) {
    String title = I18N.getText(titleKey, params);
    JList list = new JList(messages);
    JOptionPane.showMessageDialog(clientFrame, list, title, messageType);
  }

  /**
   * Displays the messages provided as <code>messages</code> by calling {@link
   * #showMessage(Object[], String, int, Object...)} and passing <code>
   * "msg.title.messageDialogFeedback"</code> and <code>JOptionPane.ERROR_MESSAGE</code> as
   * parameters.
   *
   * @param messages the Objects (normally strings) to put in the body of the dialog; no properties
   *     file lookup is performed!
   */
  public static void showFeedback(Object[] messages) {
    showMessage(messages, "msg.title.messageDialogFeedback", JOptionPane.ERROR_MESSAGE);
  }

  /**
   * Displays a dialog box by calling {@link #showError(String, Throwable)} and passing <code>null
   * </code> for the second parameter.
   *
   * @param msgKey the key to use when calling {@link I18N#getText(String)}
   */
  public static void showError(String msgKey) {
    showError(msgKey, null);
  }

  /**
   * Displays a dialog box with a predefined title and type, and a message crafted by calling {@link
   * #generateMessage(String, Throwable)} and passing it the two parameters. Also logs an entry
   * using the {@link Logger#error(Object, Throwable)} method.
   *
   * <p>The title is the property key <code>"msg.title.messageDialogError"</code> , and the dialog
   * type is <code>JOptionPane.ERROR_MESSAGE</code>.
   *
   * @param msgKey the key to use when calling {@link I18N#getText(String)}
   * @param t the exception to be processed
   */
  public static void showError(String msgKey, Throwable t) {
    String msg = generateMessage(msgKey, t);
    log.error(msgKey, t);
    showMessage(msg, "msg.title.messageDialogError", JOptionPane.ERROR_MESSAGE);
  }

  /**
   * Displays a dialog box by calling {@link #showWarning(String, Throwable)} and passing <code>null
   * </code> for the second parameter.
   *
   * @param msgKey the key to use when calling {@link I18N#getText(String)}
   */
  public static void showWarning(String msgKey) {
    showWarning(msgKey, null);
  }

  /**
   * Displays a dialog box with a predefined title and type, and a message crafted by calling {@link
   * #generateMessage(String, Throwable)} and passing it the two parameters. Also logs an entry
   * using the {@link Logger#warn(Object, Throwable)} method.
   *
   * <p>The title is the property key <code>"msg.title.messageDialogWarning"</code>, and the dialog
   * type is <code>JOptionPane.WARNING_MESSAGE</code>.
   *
   * @param msgKey the key to use when calling {@link I18N#getText(String)}
   * @param t the exception to be processed
   */
  public static void showWarning(String msgKey, Throwable t) {
    String msg = generateMessage(msgKey, t);
    log.warn(msgKey, t);
    showMessage(msg, "msg.title.messageDialogWarning", JOptionPane.WARNING_MESSAGE);
  }

  /**
   * Displays a dialog box by calling {@link #showInformation(String, Throwable)} and passing <code>
   * null</code> for the second parameter.
   *
   * @param msgKey the key to use when calling {@link I18N#getText(String)}
   */
  public static void showInformation(String msgKey) {
    showInformation(msgKey, null);
  }

  /**
   * Displays a dialog box with a predefined title and type, and a message crafted by calling {@link
   * #generateMessage(String, Throwable)} and passing it the two parameters. Also logs an entry
   * using the {@link Logger#info(Object, Throwable)} method.
   *
   * <p>The title is the property key <code>"msg.title.messageDialogInfo"</code>, and the dialog
   * type is <code>JOptionPane.INFORMATION_MESSAGE</code>.
   *
   * @param msgKey the key to use when calling {@link I18N#getText(String)}
   * @param t the exception to be processed
   */
  public static void showInformation(String msgKey, Throwable t) {
    String msg = generateMessage(msgKey, t);
    log.info(msgKey, t);
    showMessage(msg, "msg.title.messageDialogInfo", JOptionPane.INFORMATION_MESSAGE);
  }

  /**
   * Displays a confirmation dialog that uses the message as a key to the properties file, and the
   * additional values as parameters to the formatting of the key lookup.
   *
   * @param message key from the properties file (preferred) or hard-coded string to display
   * @param params optional arguments for the formatting of the property value
   * @return <code>true</code> if the user clicks the OK button, <code>false</code> otherwise
   */
  public static boolean confirm(String message, Object... params) {
    // String msg = I18N.getText(message, params);
    // log.debug(message);
    String title = I18N.getText("msg.title.messageDialogConfirm");
    // return JOptionPane.showConfirmDialog(clientFrame, msg, title, JOptionPane.OK_OPTION) ==
    // JOptionPane.OK_OPTION;
    return confirmImpl(title, JOptionPane.OK_OPTION, message, params) == JOptionPane.OK_OPTION;
  }

  /**
   * Displays a confirmation dialog that uses the message as a key to the properties file, and the
   * additional values as parameters to the formatting of the key lookup.
   *
   * @param title the title of the dialog.
   * @param buttons the buttons to display on the dialog, one of {@link JOptionPane#YES_NO_OPTION},
   *     {@link JOptionPane#YES_NO_CANCEL_OPTION}, {@link JOptionPane#OK_CANCEL_OPTION}.
   * @param message key from the properties file (preferred) or hard-coded string to display
   * @param params optional arguments for the formatting of the property value
   * @return <code>true</code> if the user clicks the OK button, <code>false</code> otherwise
   */
  public static int confirmImpl(String title, int buttons, String message, Object... params) {
    String msg = I18N.getText(message, params);
    log.debug(message);
    return JOptionPane.showConfirmDialog(clientFrame, msg, title, buttons);
  }

  /**
   * This method is specific to deleting a token, but it can be used as a basis for any other method
   * which wants to be turned off via a property.
   *
   * @return true if the token should be deleted.
   */
  public static boolean confirmTokenDelete() {
    if (!AppPreferences.getTokensWarnWhenDeleted()) {
      return true;
    }

    String msg = I18N.getText("msg.confirm.deleteToken");
    int val = confirmDelete(msg);

    // "Yes, don't show again" Button
    if (val == 2) {
      showInformation("msg.confirm.deleteToken.removed");
      AppPreferences.setTokensWarnWhenDeleted(false);
    }
    // Any version of 'Yes' returns true, false otherwise
    return val == JOptionPane.YES_OPTION || val == 2;
  }

  /**
   * Displays a dialog to confirm the delete of drawings through the Drawing Explorer window.
   * Presents Yes/No/Yes and don't ask again options. Default action is No. Button text is localized
   * as is the message.
   *
   * @return <code>true</code> if the user clicks either Yes button, <code>falsee</code> otherwise.
   */
  public static boolean confirmDrawDelete() {
    if (!AppPreferences.getDrawWarnWhenDeleted()) {
      return true;
    }

    String msg = I18N.getText("msg.confirm.deleteDraw");
    int val = confirmDelete(msg);

    // "Yes, don't show again" Button
    if (val == JOptionPane.CANCEL_OPTION) {
      showInformation("msg.confirm.deleteDraw.removed");
      AppPreferences.setDrawWarnWhenDeleted(false);
    }
    // Any version of 'Yes' returns true, otherwise false
    return val == JOptionPane.YES_OPTION || val == JOptionPane.CANCEL_OPTION;
  }

  private static int confirmDelete(String msg) {
    log.debug(msg);
    Object[] options = {
      // getText() strips out the & as when the button text is specified this way the mnemonics
      // don't work.
      I18N.getText("msg.title.messageDialog.yes"),
      I18N.getText("msg.title.messageDialog.no"),
      I18N.getText("msg.title.messageDialog.dontAskAgain")
    };
    String title = I18N.getText("msg.title.messageDialogConfirm");
    return JOptionPane.showOptionDialog(
        clientFrame,
        msg,
        title,
        JOptionPane.YES_NO_CANCEL_OPTION,
        JOptionPane.WARNING_MESSAGE,
        null,
        options,
        options[1]);
  }

  private MapTool() {
    // Not to be instantiated
    throw new Error("cannot construct MapTool object!");
  }

  /**
   * Get the BackupManager instance.
   *
   * @return the BackupManager.
   */
  public static BackupManager getBackupManager() {
    if (backupManager == null) {
      try {
        backupManager = new BackupManager(AppUtil.getAppHome("backup"));
      } catch (IOException ioe) {
        showError(I18N.getText("msg.error.creatingBackupManager"), ioe);
      }
    }
    return backupManager;
  }

  /**
   * Launch the platform's web browser and ask it to open the given URL. Note that this should not
   * be called from any uncontrolled macros as there are both security and denial-of-service attacks
   * possible.
   *
   * @param url the URL to pass to the browser.
   */
  public static void showDocument(String url) {
    if (Desktop.isDesktopSupported()) {
      Desktop desktop = Desktop.getDesktop();
      URI uri = null;
      try {
        uri = new URI(url);
        desktop.browse(uri);
      } catch (Exception e) {
        MapTool.showError(I18N.getText("msg.error.browser.cannotStart", uri), e);
      }
    } else {
      String errorMessage = "msg.error.browser.notFound";
      Exception exception = null;
      String[] envvars = {"MAPTOOL_BROWSER", "BROWSER"};
      String param = envvars[0];
      boolean apparentlyItWorked = false;
      for (String var : envvars) {
        String browser = System.getenv(var);
        if (browser != null) {
          try {
            param = var + "=\"" + browser + "\"";
            Runtime.getRuntime().exec(new String[] {browser, url});
            apparentlyItWorked = true;
          } catch (Exception e) {
            exception = e;
          }
        }
      }
      if (!apparentlyItWorked) {
        errorMessage = "msg.error.browser.cannotStart";
        MapTool.showError(I18N.getText(errorMessage, param), exception);
      }
    }
  }

  /**
   * Play the sound registered to an eventId.
   *
   * @param eventId the eventId of the sound.
   */
  public static void playSound(String eventId) {
    if (AppPreferences.getPlaySystemSounds()) {
      if (AppPreferences.getPlaySystemSoundsOnlyWhenNotFocused() && isInFocus()) {
        return;
      }
      SoundManager.playSoundEvent(eventId);
    }
  }

  public static void updateServerPolicy() {
    updateServerPolicy(serverPolicy);
  }

  public static void updateServerPolicy(ServerPolicy policy) {
    setServerPolicy(policy);

    // Give everyone the new policy
    if (serverCommand != null) {
      serverCommand.setServerPolicy(policy);
    }
  }

  public static boolean isInFocus() {
    // TODO: This should probably also check owned windows
    return getFrame().isFocused();
  }

  // TODO: This method is redundant now. It should be rolled into the
  // TODO: ExportDialog screenshot method. But until that has proven stable
  // TODO: for a while, I don't want to mess with this. (version 1.3b70 is most recent)
  public static BufferedImage takeMapScreenShot(final PlayerView view) {
    final ZoneRenderer renderer = clientFrame.getCurrentZoneRenderer();
    if (renderer == null) {
      return null;
    }

    Dimension size = renderer.getSize();
    if (size.width == 0 || size.height == 0) {
      return null;
    }

    BufferedImage image = new BufferedImage(size.width, size.height, Transparency.OPAQUE);
    final Graphics2D g = image.createGraphics();
    g.setClip(0, 0, size.width, size.height);

    // Have to do this on the EDT so that there aren't any odd side effects
    // of rendering
    // using a renderer that's on screen
    if (!EventQueue.isDispatchThread()) {
      try {
        EventQueue.invokeAndWait(() -> renderer.renderZone(g, view));
      } catch (InterruptedException | InvocationTargetException ie) {
        MapTool.showError("While creating snapshot", ie);
      }
    } else {
      renderer.renderZone(g, view);
    }

    g.dispose();

    return image;
  }

  public static AutoSaveManager getAutoSaveManager() {
    if (autoSaveManager == null) {
      autoSaveManager = new AutoSaveManager();
    }
    return autoSaveManager;
  }

  public static EventDispatcher getEventDispatcher() {
    return eventDispatcher;
  }

  private static void registerEvents() {
    getEventDispatcher().registerEvents(ZoneEvent.values());
    getEventDispatcher().registerEvents(PreferencesEvent.values());
  }

  /**
   * This was added to make it easier to set a breakpoint and locate when the frame was initialized.
   *
   * @param frame
   */
  private static void setClientFrame(MapToolFrame frame) {
    clientFrame = frame;

    if (graphicsMonitor > -1) {
      moveToMonitor(clientFrame, graphicsMonitor, useFullScreen);
    } else if (useFullScreen) {
      frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
    }
  }

  /**
   * For Multi-monitor support, allows you to move the frame to a specific monitor. It will also set
   * the height, width and x, y position of the frame.
   *
   * @param frame The JFrame to move
   * @param monitor The monitor number as an int. Note the first monitor start at 0, not 1.
   * @param maximize set to true if you want to maximize the frame to that monitor.
   * @author Jamz
   * @since 1.4.1.0
   */
  private static void moveToMonitor(JFrame frame, int monitor, boolean maximize) {
    GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
    GraphicsDevice[] gd = ge.getScreenDevices();

    if (monitor > -1 && monitor < gd.length) {
      if (windowWidth > -1 && windowHeight > -1) {
        frame.setSize(windowWidth, windowHeight);
      }

      if (windowX > -1 && windowY > -1) {
        frame.setLocation(
            windowX + gd[monitor].getDefaultConfiguration().getBounds().x,
            windowY + gd[monitor].getDefaultConfiguration().getBounds().y);

      } else {
        frame.setLocation(gd[monitor].getDefaultConfiguration().getBounds().x, frame.getY());
      }
    } else if (gd.length > 0) {
      frame.setLocation(gd[0].getDefaultConfiguration().getBounds().x, frame.getY());
    } else {
      throw new RuntimeException("No Screens Found");
    }

    if (maximize) {
      frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
    }
  }

  private static void initialize() {
    // First time
    AppSetup.install();

    // Clean up after ourselves
    FileUtil.delete(AppUtil.getAppHome("tmp"), 2);
    // We'll manage our own images
    ImageIO.setUseCache(false);

    eventDispatcher = new EventDispatcher();
    registerEvents();

    try {
      SoundManager.configure(SOUND_PROPERTIES);
      SoundManager.registerSoundEvent(
          SND_INVALID_OPERATION, SoundManager.getRegisteredSound("Dink"));
    } catch (IOException ioe) {
      MapTool.showError("While initializing (configuring sound)", ioe);
    }

    assetTransferManager = new AssetTransferManager();
    assetTransferManager.addConsumerListener(new AssetTransferHandler());

    playerList = new ObservableList<Player>();
    messageList =
        new ObservableList<TextMessage>(Collections.synchronizedList(new ArrayList<TextMessage>()));

    handler = new ClientMethodHandler();

    setClientFrame(new MapToolFrame(menuBar));

    serverCommand = new ServerCommandClientImpl();


    try {
      player = new LocalPlayer("", Player.Role.GM, ServerConfig.getPersonalServerGMPassword());
      Campaign cmpgn = CampaignFactory.createBasicCampaign();
      // This was previously being done in the server thread and didn't always get done
      // before the campaign was accessed by the postInitialize() method below.
      setCampaign(cmpgn);
      startPersonalServer(cmpgn);
    } catch (Exception e) {
      MapTool.showError("While starting personal server", e);
    }
    AppActions.updateActions();

    ToolTipManager.sharedInstance().setInitialDelay(AppPreferences.getToolTipInitialDelay());
    ToolTipManager.sharedInstance().setDismissDelay(AppPreferences.getToolTipDismissDelay());
    ChatAutoSave.changeTimeout(AppPreferences.getChatAutosaveTime());

    // TODO: make this more formal when we switch to mina
    new ServerHeartBeatThread().start();
  }

  public static NoteFrame getProfilingNoteFrame() {
    if (profilingNoteFrame == null) {
      profilingNoteFrame = new NoteFrame();
      profilingNoteFrame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
      profilingNoteFrame.addWindowListener(
          new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
              AppState.setCollectProfilingData(false);
              profilingNoteFrame.setVisible(false);
            }
          });
      profilingNoteFrame.setSize(profilingNoteFrame.getPreferredSize());
      // It's possible that the SelectionPanel may cause text to be added to the NoteFrame, so
      // it
      // can happen before MapTool.initialize() has had a chance to init the clientFrame.
      if (clientFrame != null) {
        SwingUtil.centerOver(profilingNoteFrame, clientFrame);
      }
    }
    return profilingNoteFrame;
  }

  public static JFrame getLogConsoleNoteFrame() {
    if (logConsoleFrame == null) {
      logConsoleFrame = new LogConsoleFrame();
      logConsoleFrame.setVisible(true);

      if (clientFrame != null) {
        SwingUtil.centerOver(logConsoleFrame, clientFrame);
      }
    }

    return logConsoleFrame;
  }

  public static String getVersion() {
    return version;
  }

  public static boolean isDevelopment() {
    return "DEVELOPMENT".equals(version)
        || "@buildNumber@".equals(version)
        || "0.0.1".equals(version)
        || (version != null && version.startsWith("SNAPSHOT"));
  }

  public static ServerPolicy getServerPolicy() {
    return serverPolicy;
  }

  public static ServerCommand serverCommand() {
    return serverCommand;
  }

  /** @return the server, or null if player is a client. */
  public static MapToolServer getServer() {
    return server;
  }

  public static void addPlayer(Player player) {
    if (!playerList.contains(player)) {
      playerList.add(player);

      // LATER: Make this non-anonymous
      playerList.sort((arg0, arg1) -> arg0.getName().compareToIgnoreCase(arg1.getName()));

      if (!player.equals(MapTool.getPlayer())) {
        String msg =
            MessageFormat.format(I18N.getText("msg.info.playerConnected"), player.getName());
        addLocalMessage(MessageUtil.getFormattedSystemMsg(msg));
      }
    }
  }

  public Player getPlayer(String name) {
    for (int i = 0; i < playerList.size(); i++) {
      if (playerList.get(i).getName().equals(name)) {
        return playerList.get(i);
      }
    }
    return null;
  }

  public static void removePlayer(Player player) {
    if (player == null) {
      return;
    }
    playerList.remove(player);

    if (MapTool.getPlayer() != null && !player.equals(MapTool.getPlayer())) {
      String msg =
          MessageFormat.format(I18N.getText("msg.info.playerDisconnected"), player.getName());
      addLocalMessage(MessageUtil.getFormattedSystemMsg(msg));
    }
  }

  public static ObservableList<TextMessage> getMessageList() {
    return messageList;
  }

  /**
   * These are the messages that originate from the server
   *
   * @param message the message to display
   */
  public static void addServerMessage(TextMessage message) {
    // Filter
    if (message.isGM() && !getPlayer().isGM()) {
      return;
    }
    if (message.isGmMe() && !getPlayer().isGM() && !message.isFromSelf()) {
      return;
    }
    if ((message.isNotGm() || message.isNotGmMe()) && getPlayer().isGM()) {
      return;
    }
    if ((message.isNotMe() || message.isNotGmMe()) && message.isFromSelf()) {
      return;
    }
    if (message.isWhisper() && !getPlayer().getName().equalsIgnoreCase(message.getTarget())) {
      return;
    }
    if (!getFrame().isCommandPanelVisible()) {
      getFrame().getChatActionLabel().setVisible(true);
    }
    // Flashing
    if (!isInFocus()) {
      taskbarFlasher.flash();
    }
    if (message.isWhisper()) {
      setLastWhisperer(message.getSource());
    }
    messageList.add(message);
  }

  /**
   * These are the messages that are generated locally.
   *
   * @param message The locally generated message to add.
   */
  public static void addMessage(TextMessage message) {
    // Filter stuff
    addServerMessage(message);

    if (!message.isMe()) {
      serverCommand().message(message);
    }
  }

  /**
   * Add a message only this client can see. This is a shortcut for addMessage(ME, ...)
   *
   * @param message message to be sent
   */
  public static void addLocalMessage(String message) {
    addMessage(TextMessage.me(null, message));
  }

  /**
   * Adds an error message that includes the macro stack trace.
   *
   * @param e the ParserException to display the error of
   */
  public static void addErrorMessage(ParserException e) {
    MapTool.addLocalMessage(e.getMessage());

    String[] macroStackTrace = e.getMacroStackTrace();
    if (macroStackTrace.length > 0) {
      MapTool.addLocalMessage(
          I18N.getText("msg.error.trace", String.join(" &lt;&lt;&lt; ", macroStackTrace)));
    }
  }

  /**
   * Add a message all clients can see. This is a shortcut for addMessage(SAY, ...)
   *
   * @param message message to be sent
   */
  public static void addGlobalMessage(String message) {
    addMessage(TextMessage.say(null, message));
  }

  /**
   * Add a message all specified clients will see. This is a shortcut for addMessage(WHISPER, ...)
   * and addMessage(GM, ...). The <code>targets</code> is expected do be in a string list built with
   * <code>separator</code>.
   *
   * @param message message to be sent
   * @param targets string specifying clients to send the message to (spaces are trimmed)
   * @param separator the separator between entries in <code>targets</code>
   */
  public static void addGlobalMessage(String message, String targets, String separator) {
    List<String> list = new LinkedList<String>();
    for (String target : targets.split(separator)) {
      list.add(target.trim());
    }
    addGlobalMessage(message, list);
  }

  /**
   * Add a message all specified clients will see. This is a shortcut for addMessage(WHISPER, ...)
   * and addMessage(GM, ...).
   *
   * @param message message to be sent
   * @param targets list of <code>String</code>s specifying clients to send the message to
   */
  public static void addGlobalMessage(String message, List<String> targets) {
    for (String target : targets) {
      switch (target.toLowerCase()) {
        case "gm-self":
          addMessage(TextMessage.gmMe(null, message));
          break;
        case "gm":
          addMessage(TextMessage.gm(null, message));
          break;
        case "self":
          addLocalMessage(message);
          break;
        case "not-gm":
          addMessage(TextMessage.notGm(null, message));
          break;
        case "not-self":
          addMessage(TextMessage.notMe(null, message));
          break;
        case "not-gm-self":
          addMessage(TextMessage.notGmMe(null, message));
          break;
        case "all":
          addGlobalMessage(message);
          break;
        case "none":
          break;
        default:
          addMessage(TextMessage.whisper(null, target, message));
          break;
      }
    }
  }

  public static Campaign getCampaign() {
    if (campaign == null) {
      campaign = new Campaign();
    }
    return campaign;
  }

  public static MapToolLineParser getParser() {
    return parser;
  }

  public static void setCampaign(Campaign campaign) {
    setCampaign(campaign, null);
  }

  public static void setCampaign(Campaign campaign, GUID defaultRendererId) {
    // Load up the new
    MapTool.campaign = campaign;
    ZoneRenderer currRenderer = null;

    // Clean up
    clientFrame.clearZoneRendererList();
    clientFrame.getInitiativePanel().setZone(null);
    clientFrame.clearTokenTree();
    if (campaign == null) {
      clientFrame.setCurrentZoneRenderer(null);
      return;
    }
    // Install new campaign
    for (Zone zone : campaign.getZones()) {
      ZoneRenderer renderer = ZoneRendererFactory.newRenderer(zone);
      clientFrame.addZoneRenderer(renderer);
      if ((currRenderer == null || zone.getId().equals(defaultRendererId))
          && (getPlayer().isGM() || zone.isVisible())) {
        currRenderer = renderer;
      }
      eventDispatcher.fireEvent(ZoneEvent.Added, campaign, null, zone);
    }
    clientFrame.setCurrentZoneRenderer(currRenderer);
    clientFrame.getInitiativePanel().setOwnerPermissions(campaign.isInitiativeOwnerPermissions());
    clientFrame.getInitiativePanel().setMovementLock(campaign.isInitiativeMovementLock());
    clientFrame.getInitiativePanel().setInitUseReverseSort(campaign.isInitiativeUseReverseSort());
    clientFrame
        .getInitiativePanel()
        .setInitPanelButtonsDisabled(campaign.isInitiativePanelButtonsDisabled());
    clientFrame.getInitiativePanel().updateView();

    AssetManager.updateRepositoryList();
    MapTool.getFrame().getCampaignPanel().reset();
    MapTool.getFrame().getGmPanel().reset();
    // overlay vanishes after campaign change
    MapTool.getFrame().getOverlayPanel().removeAllOverlays();
    UserDefinedMacroFunctions.getInstance().handleCampaignLoadMacroEvent();
  }

  public static void setServerPolicy(ServerPolicy policy) {
    serverPolicy = policy;
  }

  public static AssetTransferManager getAssetTransferManager() {
    return assetTransferManager;
  }

  /**
   * Start the server from a campaign file and various settings.
   *
   * @param id the id of the server for announcement.
   * @param config the server configuration.
   * @param policy the server policy configuration to use.
   * @param campaign the campaign.
   * @param playerDatabase the player database to use for the connection.
   * @param copyCampaign should the campaign be a copy of the one provided.
   * @throws IOException if new MapToolServer fails.
   */
  public static void startServer(
      String id, ServerConfig config, ServerPolicy policy, Campaign campaign,
      PlayerDatabase playerDatabase,  boolean copyCampaign)
      throws IOException {
    if (server != null) {
      Thread.dumpStack();
      showError("msg.error.alreadyRunningServer");
      return;
    }

    assetTransferManager.flush();

    // TODO: the client and server campaign MUST be different objects.
    // Figure out a better init method
    server = new MapToolServer(config, policy, playerDatabase);

    serverPolicy = server.getPolicy();
    if (copyCampaign) {
      server.setCampaign(new Campaign(campaign)); // copy of FoW depends on server policies
    } else {
      server.setCampaign(campaign);
    }

    if (announcer != null) {
      announcer.stop();
    }
    // Don't announce personal servers
    if (!config.isPersonalServer()) {
      announcer =
          new ServiceAnnouncer(id, server.getConfig().getPort(), AppConstants.SERVICE_GROUP);
      announcer.start();
    }

    // Registered ?
    if (config.isServerRegistered() && !config.isPersonalServer()) {
      try {
        MapToolRegistry.RegisterResponse result =
            MapToolRegistry.getInstance()
                .registerInstance(config.getServerName(), config.getPort());
        if (result == MapToolRegistry.RegisterResponse.NAME_EXISTS) {
          MapTool.showError("msg.error.alreadyRegistered");
        }
        // TODO: I don't like this
      } catch (Exception e) {
        MapTool.showError("msg.error.failedCannotRegisterServer", e);
      }
    }
  }

  public static ThumbnailManager getThumbnailManager() {
    if (thumbnailManager == null) {
      thumbnailManager = new ThumbnailManager(AppUtil.getAppHome("imageThumbs"), THUMBNAIL_SIZE);
    }

    return thumbnailManager;
  }

  public static void stopServer() {
    if (server == null) {
      return;
    }

    disconnect();
    server.stop();
    server = null;
  }

  public static ObservableList<Player> getPlayerList() {
    return playerList;
  }

  /** Returns the list of non-gm names. */
  public static List<String> getNonGMs() {
    List<String> nonGMs = new ArrayList<>(playerList.size());
    playerList.forEach(
        player -> {
          if (!player.isGM()) {
            nonGMs.add(player.getName());
          }
        });
    return nonGMs;
  }

  /** Returns the list of gm names. */
  public static List<String> getGMs() {
    List<String> gms = new ArrayList<>(playerList.size());
    playerList.forEach(
        player -> {
          if (player.isGM()) {
            gms.add(player.getName());
          }
        });
    return gms;
  }

  /**
   * checks if a specific player is connected to the game.
   *
   * @param player The name of the player to check.
   * @return {@code true} if the player is connected otherwise {@code false}.
   */
  public static boolean isPlayerConnected(String player) {
    for (int i = 0; i < playerList.size(); i++) {
      Player p = playerList.get(i);
      if (p.getName().equalsIgnoreCase(player)) {
        return true;
      }
    }
    return false;
  }

  public static void removeZone(Zone zone) {
    MapTool.serverCommand().removeZone(zone.getId());
    MapTool.getFrame().removeZoneRenderer(MapTool.getFrame().getZoneRenderer(zone.getId()));
    MapTool.getCampaign().removeZone(zone.getId());
  }

  public static void addZone(Zone zone) {
    addZone(zone, true);
  }

  public static void addZone(Zone zone, boolean changeZone) {
    if (getCampaign().getZones().size() == 1) {
      // Remove the default map
      Zone singleZone = getCampaign().getZones().get(0);
      if (ZoneFactory.DEFAULT_MAP_NAME.equals(singleZone.getName()) && singleZone.isEmpty()) {
        removeZone(singleZone);
      }
    }
    getCampaign().putZone(zone);
    serverCommand().putZone(zone);
    eventDispatcher.fireEvent(ZoneEvent.Added, getCampaign(), null, zone);

    // Show the new zone
    if (changeZone) {
      clientFrame.setCurrentZoneRenderer(ZoneRendererFactory.newRenderer(zone));
    } else {
      getFrame().getZoneRenderers().add(ZoneRendererFactory.newRenderer(zone));
    }
  }

  public static LocalPlayer getPlayer() {
    return player;
  }

  public static void startPersonalServer(Campaign campaign)
      throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
    ServerConfig config = ServerConfig.createPersonalServerConfig();
    PlayerDatabaseFactory.setCurrentPlayerDatabase(PERSONAL_SERVER);
    PlayerDatabase playerDatabase = PlayerDatabaseFactory.getCurrentPlayerDatabase();
    MapTool.startServer(null, config, new ServerPolicy(), campaign, playerDatabase, false);

    String username = AppPreferences.getDefaultUserName();
    LocalPlayer localPlayer = (LocalPlayer) playerDatabase.getPlayer(username);
    MapTool.createConnection( "localhost", config.getPort(), localPlayer);

    // connecting
    MapTool.getFrame().getConnectionStatusPanel().setStatus(ConnectionStatusPanel.Status.server);
  }

  public static void createConnection(String host, int port, LocalPlayer player)
      throws IOException {
    MapTool.player = player;
    MapTool.getFrame().getCommandPanel().clearAllIdentities();

    ClientConnection clientConn = new MapToolConnection(host, port, player);

    clientConn.addMessageHandler(handler);
    clientConn.addActivityListener(clientFrame.getActivityMonitor());
    clientConn.addDisconnectHandler(new ServerDisconnectHandler());

    clientConn.start();

    // LATER: I really, really, really don't like this startup pattern
    if (clientConn.isAlive()) {
      conn = clientConn;
    }
    clientFrame.getLookupTablePanel().updateView();
    clientFrame.getInitiativePanel().updateView();
  }

  public static void closeConnection() throws IOException {
    if (conn != null) {
      conn.close();
    }
  }

  public static ClientConnection getConnection() {
    return conn;
  }

  /** returns whether the player is using a personal server. */
  public static boolean isPersonalServer() {
    return server != null && server.getConfig().isPersonalServer();
  }

  /** returns whether the player is hosting a server - personal servers do not count. */
  public static boolean isHostingServer() {
    return server != null && !server.getConfig().isPersonalServer();
  }

  public static void disconnect() {
    // Close UPnP port mapping if used
    StartServerDialogPreferences serverProps = new StartServerDialogPreferences();
    if (serverProps.getUseUPnP()) {
      int port = serverProps.getPort();
      UPnPUtil.closePort(port);
    }
    boolean isPersonalServer = isPersonalServer();

    if (announcer != null) {
      announcer.stop();
      announcer = null;
    }
    if (conn == null || !conn.isAlive()) {
      return;
    }
    // Unregister ourselves
    if (server != null && server.getConfig().isServerRegistered() && !isPersonalServer) {
      try {
        MapToolRegistry.getInstance().unregisterInstance();
      } catch (Throwable t) {
        MapTool.showError("While unregistering server instance", t);
      }
    }

    try {
      conn.close();
      conn = null;
      playerList.clear();
    } catch (IOException ioe) {
      // This isn't critical, we're closing it anyway
      log.debug("While closing connection", ioe);
    }
    MapTool.getFrame()
        .getConnectionStatusPanel()
        .setStatus(ConnectionStatusPanel.Status.disconnected);

    if (!isPersonalServer) {
      addLocalMessage(MessageUtil.getFormattedSystemMsg(I18N.getText("msg.info.disconnected")));
    }
  }

  public static MapToolFrame getFrame() {
    return clientFrame;
  }

  private static void configureJide() {
    LookAndFeelFactory.UIDefaultsCustomizer uiDefaultsCustomizer =
        defaults -> {
          ThemePainter painter = (ThemePainter) UIDefaultsLookup.get("Theme.painter");
          defaults.put("OptionPaneUI", "com.jidesoft.plaf.basic.BasicJideOptionPaneUI");

          defaults.put("OptionPane.showBanner", Boolean.TRUE); // show banner or not. default
          // is true
          defaults.put(
              "OptionPane.bannerIcon",
              new ImageIcon(
                  MapTool.class
                      .getClassLoader()
                      .getResource("net/rptools/maptool/client/image/maptool_icon.png")));
          defaults.put("OptionPane.bannerFontSize", 13);
          defaults.put("OptionPane.bannerFontStyle", Font.BOLD);
          defaults.put("OptionPane.bannerMaxCharsPerLine", 60);
          defaults.put(
              "OptionPane.bannerForeground",
              painter != null ? painter.getOptionPaneBannerForeground() : null); // you
          // should
          // adjust
          // this
          // if
          // banner
          // background
          // is
          // not
          // the
          // default
          // gradient paint
          defaults.put("OptionPane.bannerBorder", null); // use default border

          // set both bannerBackgroundDk and bannerBackgroundLt to null if you don't want
          // gradient
          defaults.put(
              "OptionPane.bannerBackgroundDk",
              painter != null ? painter.getOptionPaneBannerDk() : null);
          defaults.put(
              "OptionPane.bannerBackgroundLt",
              painter != null ? painter.getOptionPaneBannerLt() : null);
          defaults.put("OptionPane.bannerBackgroundDirection", Boolean.TRUE); // default is
          // true

          // optionally, you can set a Paint object for BannerPanel. If so, the three
          // UIDefaults
          // related to banner background above will be ignored.
          defaults.put("OptionPane.bannerBackgroundPaint", null);

          defaults.put("OptionPane.buttonAreaBorder", BorderFactory.createEmptyBorder(6, 6, 6, 6));
          defaults.put("OptionPane.buttonOrientation", SwingConstants.RIGHT);
        };
    uiDefaultsCustomizer.customize(UIManager.getDefaults());
  }

  private static void postInitialize() {
    // Check to see if there is an autosave file from MT crashing
    getAutoSaveManager().check();

    if (!loadCampaignOnStartPath.isEmpty()) {
      File campaignFile = new File(loadCampaignOnStartPath);
      if (campaignFile.exists()) {
        AppActions.loadCampaign(campaignFile);
      }
    }

    // fire up autosaves
    getAutoSaveManager().start();

    taskbarFlasher = new TaskBarFlasher(clientFrame);

    // Jamz: After preferences are loaded, Asset Tree and ImagePanel are out of sync,
    // so after frame is all done loading we sync them back up.
    MapTool.getFrame().getAssetPanel().getAssetTree().initialize();

    // Set the Topology drawing mode to the last mode used for convenience
    MapTool.getFrame()
        .getCurrentZoneRenderer()
        .getZone()
        .setTopologyMode(AppPreferences.getTopologyDrawingMode());

    // Initialize the web end point
    try {
      WebEndPoint.getWebEndPoint();
    } catch (Exception e) {
      log.error(I18N.getText("msg.error.cantStartWebEndPoint"), e);
      MapTool.showError("msg.error.cantStartWebEndPoint");
    }
  }

  /**
   * Return whether the campaign file has changed. Only checks to see if there is a single empty map
   * with the default name (ZoneFactory.DEFAULT_MAP_NAME). If so, the campaign is "empty". The right
   * way to do this is to check the length of the UndoQueue -- if the length is zero, we know the
   * data isn't dirty. But that would require a working UndoQueue... :(
   *
   * @return {@code true} if the campaign file has changed, otherwise {@code false}.
   */
  public static boolean isCampaignDirty() {
    // TODO: This is a very naive check, but it's better than nothing
    if (getCampaign().getZones().size() == 1) {
      Zone singleZone = MapTool.getCampaign().getZones().get(0);
      if (ZoneFactory.DEFAULT_MAP_NAME.equals(singleZone.getName()) && singleZone.isEmpty()) {
        return false;
      }
    }
    return true;
  }

  public static void setLastWhisperer(String lastWhisperer) {
    if (lastWhisperer != null) {
      MapTool.lastWhisperer = lastWhisperer;
    }
  }

  public static String getLastWhisperer() {
    return lastWhisperer;
  }

  public static boolean useToolTipsForUnformatedRolls() {
    if (isPersonalServer() || getServerPolicy() == null) {
      return AppPreferences.getUseToolTipForInlineRoll();
    } else {
      return getServerPolicy().getUseToolTipsForDefaultRollFormat();
    }
  }

  public static MTWebAppServer getWebAppServer() {
    return webAppServer;
  }

  public static void startWebAppServer(final int port) {
    try {
      Thread webAppThread =
          new Thread(
              () -> {
                webAppServer.setPort(port);
                webAppServer.startServer();
              });

      webAppThread.start();
    } catch (Exception e) { // TODO: This needs to be logged
      System.out.println("Unable to start web server");
      e.printStackTrace();
    }
  }

  public static String getClientId() {
    return clientId;
  }

  private static class ServerHeartBeatThread extends Thread {

    @Override
    public void run() {

      // This should always run, so we should be able to safely
      // loop forever
      while (true) {
        try {
          Thread.sleep(20000);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }

        ServerCommand command = serverCommand;
        if (command != null) {
          command.heartbeat(getPlayer().getName());
        }
      }
    }
  }

  /**
   * Search for command line arguments for options. Expecting arguments specified as
   * -parameter=value pair and returns a string.
   *
   * <p>Examples: -version=1.4.0.1 -user=Jamz
   *
   * @param cmd {@link org.apache.commons.cli.Options}
   * @param searchValue Option string to search for, ie -version
   * @param defaultValue A default value to return if option is not found
   * @return Option value found as a String, or defaultValue if not found
   * @author Jamz
   * @since 1.5.12
   */
  private static String getCommandLineOption(
      CommandLine cmd, String searchValue, String defaultValue) {
    return cmd.hasOption(searchValue) ? cmd.getOptionValue(searchValue) : defaultValue;
  }

  /**
   * Search for command line arguments for options. Expecting arguments formatted as a switch
   *
   * <p>Examples: -x or -fullscreen
   *
   * @param cmd {@link org.apache.commons.cli.Options}
   * @param searchValue Option string to search for, ie -version
   * @return A boolean value of true if option parameter found
   * @author Jamz
   * @since 1.5.12
   */
  private static boolean getCommandLineOption(CommandLine cmd, String searchValue) {
    return cmd.hasOption(searchValue);
  }

  /**
   * Search for command line arguments for options. Expecting arguments specified as
   * -parameter=value pair and returns a string.
   *
   * <p>Examples: -monitor=1 -x=0 -y=0 -w=1200 -h=960
   *
   * @param cmd {@link org.apache.commons.cli.Options}
   * @param searchValue Option string to search for, ie -version
   * @param defaultValue A default value to return if option is not found
   * @return Int value of the matching option parameter if found
   * @author Jamz
   * @since 1.5.12
   */
  private static int getCommandLineOption(CommandLine cmd, String searchValue, int defaultValue) {
    return StringUtil.parseInteger(cmd.getOptionValue(searchValue), defaultValue);
  }

  /** An example method that throws an exception. */
  static void unsafeMethod() {
    throw new UnsupportedOperationException("You shouldn't call this either!");
  }

  /** Examples using the (recommended) static API. */
  static void testSentryAPI() {
    // Note that all fields set on the context are optional. Context data is copied onto
    // all future events in the current context (until the context is cleared).

    // Record a breadcrumb in the current context. By default the last 100 breadcrumbs are kept.
    Sentry.getContext()
        .recordBreadcrumb(new BreadcrumbBuilder().setMessage("User made an action").build());

    // Set the user in the current context.
    Sentry.getContext().setUser(new UserBuilder().setEmail("hello@sentry.io").build());

    // Add extra data to future events in this context.
    Sentry.getContext().addExtra("extra", "thing");

    // Add an additional tag to future events in this context.
    Sentry.getContext().addTag("tagName", "tagValue");

    /*
     * This sends a simple event to Sentry using the statically stored instance that was created in the ``main`` method.
     */
    Sentry.capture("This is another logWithStaticAPI test");

    try {
      unsafeMethod();
    } catch (Exception e) {
      // This sends an exception event to Sentry using the statically stored instance
      // that was created in the ``main`` method.
      Sentry.capture(e);
    }
  }

  public static String getLoggerFileName() {
    org.apache.logging.log4j.core.Logger loggerImpl = (org.apache.logging.log4j.core.Logger) log;
    Appender appender = loggerImpl.getAppenders().get("LogFile");

    if (appender != null) {
      if (appender instanceof FileAppender) {
        return ((FileAppender) appender).getFileName();
      } else if (appender instanceof RollingFileAppender) {
        return ((RollingFileAppender) appender).getFileName();
      }
    }

    return "NOT_CONFIGURED";
  }

  public static void main(String[] args) {
    log.info("********************************************************************************");
    log.info("**                                                                            **");
    log.info("**                              MapTool Started!                              **");
    log.info("**                                                                            **");
    log.info("********************************************************************************");
    log.info("Logging to: " + getLoggerFileName());

    String versionImplementation = version;
    String versionOverride = version;

    if (MapTool.class.getPackage().getImplementationVersion() != null) {
      versionImplementation = MapTool.class.getPackage().getImplementationVersion().trim();
      log.info("getting MapTool version from manifest: " + versionImplementation);
    }

    if (MapTool.class.getPackage().getImplementationVendor() != null) {
      vendor = MapTool.class.getPackage().getImplementationVendor().trim();
      log.info("getting MapTool vendor from manifest:  " + vendor);
    }

    // Initialize Sentry.io logging
    Sentry.init();
    sentry = SentryClientFactory.sentryClient();
    // testSentryAPI(); // purely for testing...

    // Jamz: Overwrite version for testing if passed as command line argument using -v or
    // -version
    Options cmdOptions = new Options();
    cmdOptions.addOption("d", "debug", false, "turn on System.out enhanced debug output");
    cmdOptions.addOption("v", "version", true, "override MapTool version");
    cmdOptions.addOption("m", "monitor", true, "sets which monitor to use");
    cmdOptions.addOption("f", "fullscreen", false, "set to maximize window");
    cmdOptions.addOption("w", "width", true, "override MapTool window width");
    cmdOptions.addOption("h", "height", true, "override MapTool window height");
    cmdOptions.addOption("x", "xpos", true, "override MapTool window starting x coordinate");
    cmdOptions.addOption("y", "ypos", true, "override MapTool window starting y coordinate");
    cmdOptions.addOption("m", "macros", false, "display defined list of macro functions");
    cmdOptions.addOption("r", "reset", false, "reset startup options to defaults");
    cmdOptions.addOption("F", "file", true, "load campaign on startup");

    CommandLineParser cmdParser = new DefaultParser();
    CommandLine cmd = null;
    boolean listMacros = false;

    try {
      cmd = cmdParser.parse(cmdOptions, args);

      debug = getCommandLineOption(cmd, "debug");
      versionOverride = getCommandLineOption(cmd, "version", version);
      graphicsMonitor = getCommandLineOption(cmd, "monitor", graphicsMonitor);
      useFullScreen = getCommandLineOption(cmd, "fullscreen");

      windowWidth = getCommandLineOption(cmd, "width", windowWidth);
      windowHeight = getCommandLineOption(cmd, "height", windowHeight);
      windowX = getCommandLineOption(cmd, "xpos", windowX);
      windowY = getCommandLineOption(cmd, "ypos", windowY);

      loadCampaignOnStartPath = getCommandLineOption(cmd, "file", "");
      listMacros = getCommandLineOption(cmd, "macros");

      if (getCommandLineOption(cmd, "reset")) {
        UserJvmOptions.resetJvmOptions();
      }
    } catch (ParseException e) {
      // MapTool.showWarning() can be invoked here.  It will log the stacktrace,
      // so there's no need for us to do it.
      MapTool.showWarning("Error parsing the command line", e);
    }

    // Jamz: Just a little console log formatter for system.out to hyperlink messages to source.
    if (debug) {
      Configurator.setRootLevel(Level.DEBUG);
      DebugStream.activate();
    } else {
      DebugStream.deactivate();
    }

    // List out passed in arguments
    for (String arg : args) {
      log.info("argument passed via command line: " + arg);
    }

    if (cmd.hasOption("version")) {
      log.info("overriding MapTool version from command line to: " + versionOverride);
      version = versionOverride;
    } else {
      version = versionImplementation;
      log.info("MapTool version: " + version);
    }

    log.info("MapTool vendor: " + vendor);

    if (cmd.getArgs().length != 0) {
      log.info("Overriding -F option with extra argument");
      loadCampaignOnStartPath = cmd.getArgs()[0];
    }
    if (!loadCampaignOnStartPath.isEmpty()) {
      log.info("Loading initial campaign: " + loadCampaignOnStartPath);
    }

    // Set MapTool version
    sentry.setRelease(getVersion());
    sentry.addTag("os", System.getProperty("os.name"));
    sentry.addTag("version", MapTool.getVersion());
    sentry.addTag("versionImplementation", versionImplementation);
    sentry.addTag("versionOverride", versionOverride);

    if (listMacros) {
      StringBuilder logOutput = new StringBuilder();
      List<String> macroList = new ArrayList<>(parser.listAllMacroFunctions().keySet());
      Collections.sort(macroList);

      for (String macro : macroList) {
        logOutput.append("\n").append(macro);
      }

      log.info("Current list of Macro Functions: " + logOutput);
    }

    if (AppUtil.MAC_OS_X) {
      // On OSX the menu bar at the top of the screen can be enabled at any time, but the
      // title (ie. name of the application) has to be set before the GUI is initialized (by
      // creating a frame, loading a splash screen, etc). So we do it here.
      System.setProperty("apple.laf.useScreenMenuBar", "true");
      System.setProperty("com.apple.mrj.application.apple.menu.about.name", "About MapTool...");
      System.setProperty("apple.awt.brushMetalLook", "true");
    }

    // System properties
    System.setProperty("swing.aatext", "true");

    final SplashScreen splash = new SplashScreen((isDevelopment()) ? getVersion() : getVersion());

    // Protocol handlers
    // cp:// is registered by the RPTURLStreamHandlerFactory constructor (why?)
    RPTURLStreamHandlerFactory factory = new RPTURLStreamHandlerFactory();
    factory.registerProtocol("asset", new AssetURLStreamHandler());

    // Syrinscape Protocols
    if (AppPreferences.getSyrinscapeActive()) {
      factory.registerProtocol("syrinscape-fantasy", new SyrinscapeURLStreamHandler());
      factory.registerProtocol("syrinscape-sci-fi", new SyrinscapeURLStreamHandler());
      factory.registerProtocol("syrinscape-boardgame", new SyrinscapeURLStreamHandler());
    }

    URL.setURLStreamHandlerFactory(factory);

    // Register ImageReaderSpi for jpeg2000 from JAI manually (issue due to uberJar packaging)
    // https://github.com/jai-imageio/jai-imageio-core/issues/29
    IIORegistry registry = IIORegistry.getDefaultInstance();
    registry.registerServiceProvider(new com.github.jaiimageio.jpeg2000.impl.J2KImageReaderSpi());

    final Toolkit tk = Toolkit.getDefaultToolkit();
    tk.getSystemEventQueue().push(new MapToolEventQueue());

    // LAF
    try {
      // If we are running under Mac OS X then save native menu bar look & feel components
      // Note the order of creation for the AppMenuBar, this specific chronology
      // allows the system to set up system defaults before we go and modify things.
      // That is, please don't move these lines around unless you test the result on windows
      // and mac
      if (AppUtil.MAC_OS_X) {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        UIManager.setLookAndFeel(AppUtil.LOOK_AND_FEEL_NAME);
        menuBar = new AppMenuBar();
        OSXAdapter.macOSXicon();
        loadTheme();
      }
      // If running on Windows based OS, CJK font is broken when using TinyLAF.
      // else if (WINDOWS) {
      // UIManager.setLookAndFeel("com.sun.java.swing.plaf.windows.WindowsLookAndFeel");
      // menuBar = new AppMenuBar();
      // }
      else {
        UIManager.setLookAndFeel(AppUtil.LOOK_AND_FEEL_NAME);
        loadTheme();
        menuBar = new AppMenuBar();
      }

      com.jidesoft.utils.Lm.verifyLicense(
          "Trevor Croft", "rptools", "5MfIVe:WXJBDrToeLWPhMv3kI2s3VFo");
      LookAndFeelFactory.addUIDefaultsCustomizer(
          defaults -> {
            // Remove red border around menus
            defaults.put("PopupMenu.foreground", Color.lightGray);
          });
      LookAndFeelFactory.installJideExtension(LookAndFeelFactory.XERTO_STYLE);

      configureJide();
    } catch (Exception e) {
      MapTool.showError("msg.error.lafSetup", e);
      System.exit(1);
    }

    /**
     * This is a tweak that makes the Chinese version work better.
     *
     * <p>Consider reviewing <a href="http://en.wikipedia.org/wiki/CJK_characters" >http://en.
     * wikipedia.org/wiki/CJK_characters</a> before making changes. And
     * http://www.scarfboy.com/coding/unicode-tool is also a really cool site.
     */
    if (Locale.CHINA.equals(Locale.getDefault())) {
      // The following font name appears to be "Sim Sun". It can be downloaded
      // from here: http://fr.cooltext.com/Fonts-Unicode-Chinese
      Font f = new Font("\u65B0\u5B8B\u4F53", Font.PLAIN, 12);
      FontUIResource fontRes = new FontUIResource(f);
      for (Iterator<Object> iterator = UIManager.getDefaults().keySet().iterator();
          iterator.hasNext(); ) {
        Object key = iterator.next();
        Object value = UIManager.get(key);
        if (value instanceof FontUIResource) {
          UIManager.put(key, fontRes);
        }
      }
    }

    // Draw frame contents on resize
    tk.setDynamicLayout(true);

    EventQueue.invokeLater(
        () -> {
          initialize();
          EventQueue.invokeLater(
              () -> {
                clientFrame.setVisible(true);
                splash.hideSplashScreen();
                EventQueue.invokeLater(MapTool::postInitialize);
              });
        });
    // new Thread(new HeapSpy()).start();
  }

  private static void loadTheme()
      throws ClassNotFoundException, InstantiationException, IllegalAccessException,
          UnsupportedLookAndFeelException {
    // After the TinyLAF library is initialized, look to see if there is a Default.theme
    // in our AppHome directory and load it if there is. Unfortunately, changing the
    // search path for the default theme requires subclassing TinyLAF and because
    // we have both the original and a Mac version that gets cumbersome. (Really
    // the Mac version should use the default and then install the keystroke differences
    // but what we have works and I'm loathe to go playing with it at 1.3b87 -- yes, 87!)
    File f2 = AppUtil.getThemeFile(AppUtil.getThemeName());
    // File f = AppUtil.getAppHome("config");
    // if (f.exists()) {
    // File f2 = new File(f, "Default.theme");
    if (f2 != null && f2.exists() && Theme.loadTheme(f2)) {
      // re-install the Tiny Look and Feel
      UIManager.setLookAndFeel(AppUtil.LOOK_AND_FEEL_NAME);

      // Update the ComponentUIs for all Components. This
      // needs to be invoked for all windows.
      // SwingUtilities.updateComponentTreeUI(rootWindow);
    } else {
      showMessage(
          "msg.error.cantLoadTheme",
          "msg.error.cantLoadThemeTitle",
          JOptionPane.WARNING_MESSAGE,
          AppUtil.getThemeName());
      AppUtil.setThemeName(AppConstants.DEFAULT_THEME_NAME);
    }
    // }
  }
}
