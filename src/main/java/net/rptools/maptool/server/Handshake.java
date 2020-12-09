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
package net.rptools.maptool.server;

import com.caucho.hessian.io.HessianInput;
import com.caucho.hessian.io.HessianOutput;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;
import net.rptools.maptool.client.MapTool;
import net.rptools.maptool.language.I18N;
import net.rptools.maptool.model.Player;
import net.rptools.maptool.model.Player.Role;
import net.rptools.maptool.util.CipherUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** @author trevor */
public class Handshake {

  public interface Code {
    public static final int UNKNOWN = 0;
    public static final int OK = 1;
    public static final int ERROR = 2;
  }

  private static String USERNAME_FIELD = "username:";
  private static String VERSION_FIELD = "version:";

  /** Instance used for log messages. */
  private static final Logger log = LogManager.getLogger(MapToolServerConnection.class);

  /**
   * Server side of the handshake
   *
   * @param server the MapTool server instance
   * @param s the server socket
   * @throws IOException if an I/O error occurs when creating the input stream, the socket is
   *     closed, the socket is not connected, or the socket input has been shutdown using
   * @return A player structure for the connected player or null on issues
   * @throws IOException if there is a problem reading from the socket.
   */
  public static Player receiveHandshake(MapToolServer server, Socket s) throws IOException {

    Response response = new Response();
    Request request =
        decodeRequest(
            s, server.getConfig().getPlayerPasswordKey(), server.getConfig().getGMPasswordKey());
    if (request == null) {
      response.code = Code.ERROR;
      response.message = I18N.getString("Handshake.msg.wrongPassword");
    } else if (server.isPlayerConnected(request.name)) { // Enforce a unique name
      response.code = Code.ERROR;
      response.message = I18N.getString("Handshake.msg.duplicateName");
    } else if (!MapTool.isDevelopment()
        && !MapTool.getVersion().equals(request.version)
        && !"DEVELOPMENT".equals(request.version)
        && !"@buildNumber@".equals(request.version)) {
      // Allows a version running without a 'version.txt' to act as client or server to any other
      // version

      response.code = Code.ERROR;
      String clientUsed = request.version;
      String serverUsed = MapTool.getVersion();
      response.message = I18N.getText("Handshake.msg.wrongVersion", clientUsed, serverUsed);
    } else {
      response.code = Code.OK;
    }

    HessianOutput output = new HessianOutput(s.getOutputStream());
    output.getSerializerFactory().setAllowNonSerializable(true);

    response.policy = server.getPolicy();
    output.writeObject(response);
    return response.code == Code.OK
        ? new Player(request.name, Player.Role.valueOf(request.role), request.password)
        : null;
  }

  private static Request extractRequestDetails(byte[] bytes, Role role) {
    String[] lines = new String(bytes).split("\n");
    Request request = new Request();
    for (String line : lines) {
      if (line.startsWith(USERNAME_FIELD)) {
        request.name = line.replace(USERNAME_FIELD, "");
      } else if (line.startsWith(VERSION_FIELD)) {
        request.version = line.replace(VERSION_FIELD, "");
      }
    }

    if (request.name != null && request.version != null) {
      request.role = role.name();
      request.password = ""; // It doesn't really matter
      return request;
    } else {
      return null;
    }
  }

  /**
   * Decrypts the handshake / login request.
   *
   * @param socket The network socket for the connection.
   * @param playerPasswordKey
   * @param gmPasswordKey
   * @return The decrypted {@link Request}.
   */
  private static Request decodeRequest(
      Socket socket, SecretKeySpec playerPasswordKey, SecretKeySpec gmPasswordKey)
      throws IOException {
    InputStream inputStream = socket.getInputStream();
    DataInputStream dis = new DataInputStream(inputStream);
    int numBytes = dis.readInt();
    byte[] text = dis.readNBytes(numBytes);
    byte[] decrypted = null;
    Exception playerEx = null;
    Exception gmEx = null;

    // First try to decode with the player password
    try {
      Cipher playerCipher = CipherUtil.getInstance().createDecrypter(playerPasswordKey);
      decrypted = playerCipher.doFinal(text);
    } catch (Exception ex) {
      playerEx = ex;
      // Do nothing as we will report error later if GM password also fails
    }

    Request request = null;
    if (decrypted != null) {
      request = extractRequestDetails(decrypted, Role.PLAYER);
    }

    if (request == null) {
      try {
        Cipher gmCipher = CipherUtil.getInstance().createDecrypter(gmPasswordKey);
        decrypted = gmCipher.doFinal(text);
        request = extractRequestDetails(decrypted, Role.GM);
      } catch (Exception ex) {
        gmEx = ex;
        // Do nothing as weil will report error along with player password error.
      }
    }

    if (playerEx != null || gmEx != null) {
      log.warn(I18N.getText("Handshake.msg.failedLogin", socket.getInetAddress()));
      log.warn(I18N.getText("Handshake.msg.failedLoginPlayer", playerEx));
      log.warn(I18N.getText("Handshake.msg.failedLoginGM", gmEx));
    }

    return request;
  }

  /**
   * Client side of the handshake
   *
   * @param request the handshake request
   * @param s the socket to send the request on
   * @throws IOException if an I/O error occurs when creating the input stream, the socket is
   *     closed, the socket is not connected, or the socket input has been shutdown using
   * @return the response from the srever
   */
  public static Response sendHandshake(Request request, Socket s)
      throws IOException, IllegalBlockSizeException, InvalidKeyException, BadPaddingException,
          NoSuchAlgorithmException, NoSuchPaddingException {
    HessianInput input = new HessianInput(s.getInputStream());
    // HessianOutput output = new HessianOutput(s.getOutputStream());
    // Jamz: Method renamed in Hessian 4.0.+
    // output.findSerializerFactory().setAllowNonSerializable(true);
    // output.getSerializerFactory().setAllowNonSerializable(true);
    // output.writeObject(request);

    byte[] reqBytes = buildRequest(request);
    OutputStream out;
    DataOutputStream dos = new DataOutputStream(s.getOutputStream());
    dos.writeInt(reqBytes.length);
    dos.write(reqBytes);
    dos.flush();

    return (Response) input.readObject();
  }

  private static byte[] buildRequest(Request request)
      throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException,
          BadPaddingException, IllegalBlockSizeException {
    StringBuilder sb = new StringBuilder();
    sb.append(USERNAME_FIELD);
    sb.append(request.name);
    sb.append("\n");
    sb.append(VERSION_FIELD);
    sb.append(request.version);
    sb.append("\n");

    Cipher cipher = CipherUtil.getInstance().createEncrypter(request.password);
    return cipher.doFinal(sb.toString().getBytes(StandardCharsets.UTF_8));
  }

  public static class Request {
    public String name;
    public String role;
    public String password;
    public String version;

    public Request() {
      // for serialization
    }

    public Request(String name, String password, Player.Role role, String version) {
      this.name = name;
      this.password = password;
      this.role = role.name();
      this.version = version;
    }
  }

  public static class Response {
    public int code;
    public String message;
    public ServerPolicy policy;
  }
}
