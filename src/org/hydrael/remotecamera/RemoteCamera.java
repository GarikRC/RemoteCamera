package org.hydrael.remotecamera;

import android.app.Activity;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.widget.TextView;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.EmptyStackException;
import java.util.Stack;

public class RemoteCamera extends Activity {

  private Camera camera = null;
  private Camera.PictureCallback pictureCallback = null;
  private ServerSocket socket;
  private Thread listeningThread;
  private Thread connectionThread;
  /** Signals if the listening thread should be killed. */
  private boolean isDead = false;

  private static final String LOG_TAG = "REMOTECAMERA";
  /** The port on which the server listens. */
  private static final int SERVER_PORT = 4711;

  private static final int PREVIEW_FRAMERATE = 10;

  private static final int PREVIEW_WIDTH = 320;

  private static final int PREVIEW_HEIGHT = 240;

  private static final int JPEG_QUALITY = 85;

  private TextView textView;

  private Parameters parameters;

  /** A stack that contains all connected clients.
   * The taken picture will be sent to the clients in this stack.
   */
  private Stack<Socket> clients = new Stack<Socket>();

  /**
   * Dummy surface which is needed to grab the camera preview.
   */
  private SurfaceView dummySurface;

  private byte[] lastPreviewFrame = null;

  /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      setContentView(R.layout.main);
      textView = (TextView)findViewById(R.id.textview);
      dummySurface = new SurfaceView(this);
      dummySurface.setVisibility(1);

      try {
        setupServer();
      } catch ( Exception ex ) {
        Log.e(LOG_TAG, "Error setting up :" + ex.getMessage());
      }
  }

  @Override
  protected void onPause() {
    super.onPause();
    if ( camera != null ) {
      camera.setPreviewCallback(null);
      camera.release();
      camera = null;
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
    setupCamera();
    camera.startPreview();
  }

  /**
   * Sets up the TCP/IP server socket.
   */
  private void setupServer() throws IOException {
    Log.d(LOG_TAG, "Setting up server...");
    socket = new ServerSocket(SERVER_PORT);
    Log.i(LOG_TAG, "Listening on " + socket.getInetAddress().getHostAddress());
    connectionThread = new Thread(new Runnable() {
      public void run() {
        try {
          while ( !isDead ) {
            Socket client = socket.accept();
            clients.push(client);
          }
        } catch ( Exception ex ) {
          Log.e(LOG_TAG, "Error accepting connection: " + ex.getMessage());
        }
      }
    });
    connectionThread.start();

    listeningThread = new Thread(new Runnable() {
      public void run() {
        Log.d(LOG_TAG, "Started listening thread");
        while ( !isDead ) {
          try {
            for ( Socket client : clients ) {
              BufferedReader in = new BufferedReader(
                  new InputStreamReader(client.getInputStream()));
              String str = in.readLine();
              Log.d(LOG_TAG, "Received: " + str);
              if (str.equals("are_you_there_bro")) {
                shakeHand(client);
              } else if (str.equals("take_a_picture_dude")) {
                takePicture();
              } else if (str.equals("what's_going_on_mate")) {
                sendLastPreviewFrame(client);
              }
            }
          } catch ( Exception ex ) {
            Log.e(
                LOG_TAG, "Exception in socket thread: " + ex.getMessage());
          }
          try {
            Thread.sleep(100);
          } catch ( Exception ignore ) {
          }
        }
      }
    });
    listeningThread.start();
    Log.d(LOG_TAG, "Finished setting up server...");
  }

  private void shakeHand(Socket client) {
      try {
        BufferedWriter w = new BufferedWriter(new OutputStreamWriter(
            new BufferedOutputStream(client.getOutputStream())));
        w.write("sure_thing_buddy");
        w.flush();
      } catch ( IOException e ) {
        Log.e(LOG_TAG, "Shaking hands" + e.getMessage());
      }
  }

  /**
   * Sends the last preview frame to all connected clients.
   */
  private void sendLastPreviewFrame(Socket c) {
    if ( lastPreviewFrame != null && lastPreviewFrame.length > 0 ) {
      try {
        c.getOutputStream().write(lastPreviewFrame);
      } catch ( IOException e ) {
        Log.e(LOG_TAG, "Error sending preview frame data: " + e.getMessage());
      }
    }
  }

  /**
   * Connects to the camera and sets up the pictureCallback.
   */
  private void setupCamera() {
    Log.d(LOG_TAG, "Setting up camera");
    camera = Camera.open();
    parameters = camera.getParameters();
    parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
    parameters.setFlashMode(Camera.Parameters.FLASH_MODE_ON);
    parameters.setJpegQuality(JPEG_QUALITY);
    parameters.setRotation(270);
    parameters.setPreviewFrameRate(PREVIEW_FRAMERATE);
    parameters.setPreviewSize(PREVIEW_WIDTH, PREVIEW_HEIGHT);
    camera.setParameters(parameters);
    try {
      camera.setPreviewDisplay(dummySurface.getHolder());
      camera.setPreviewCallback(new Camera.PreviewCallback() {
        public void onPreviewFrame(byte[] frameData, Camera camera) {
          try {
            int pw = PREVIEW_WIDTH;
            int ph = PREVIEW_HEIGHT;
            // setting the preview image format to jpeg causes an error.
            // that's why the yuv stream has to be converted manually.
            // see http://code.google.com/p/android/issues/detail?id=823
            YuvImage yuv = new YuvImage(
                frameData, ImageFormat.NV21, pw, ph, null);
            ByteArrayOutputStream s = new ByteArrayOutputStream();
            yuv.compressToJpeg(new Rect(0, 0, pw, ph), JPEG_QUALITY, s);
            lastPreviewFrame = s.toByteArray();
            s.close();
          } catch ( Exception ex ) {
            Log.e(LOG_TAG, "Error converting preview frame to jpeg");
          }
        }
      });
    } catch (IOException ex) {
      Log.e(LOG_TAG, "Error setting up preview display.");
    }
    pictureCallback = new Camera.PictureCallback() {
      public void onPictureTaken(byte[] imageTaken, Camera camera) {
        Log.d(LOG_TAG, "Picture taken");
        for ( Socket c : clients ) {
          try {
            c.getOutputStream().write(imageTaken);
            String adr =  c.getInetAddress().getHostAddress();
            Log.i(LOG_TAG, "Sending picture to " + adr);
            textView.setText("Sending picture to " + adr);
          } catch ( IOException ex ) {
            Log.e(LOG_TAG, "Error sending image data: " + ex.getMessage());
          }
        }
      }
    };
  }

  /**
   * Takes a picture.
   */
  private void takePicture() {
    camera.autoFocus(new Camera.AutoFocusCallback() {
      public void onAutoFocus(boolean success, Camera camera) {
        if ( success ) {
          camera.takePicture(null, null, pictureCallback);
        } else {
          Log.e(LOG_TAG, "Autofocus failed");
        }
      }
    });
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    if (listeningThread.isAlive()) {
      isDead = true;
    }
  }

  public void die(View ignore) {
    try {
      while ( true ) {
        Socket c = clients.pop();
        try {
          c.close();
        } catch ( IOException ex ) {
          Log.e(LOG_TAG, "Error closing connection: " + ex.getMessage());
        }
      }
    } catch ( EmptyStackException alsoIgnore ) {
      // done
    }
    finish();
  }
}
