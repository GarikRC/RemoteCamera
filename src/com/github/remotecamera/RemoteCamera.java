package com.github.remotecamera;

import android.app.Activity;
import android.hardware.Camera;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import java.io.BufferedReader;
import java.io.BufferedWriter;
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
  private boolean isListening = false;
  private Thread listeningThread;
  /** Signals if the listening thread should be killed. */
  private boolean isDead = false;

  private static final String LOG_TAG = "REMOTECAMERA";
  /** The port on which the server listens. */
  private static final int SERVER_PORT = 4711;

  private TextView textView;

  /** A stack that contains all connected clients.
   * The taken picture will be sent to the clients in this stack.
   */
  private Stack<Socket> clients = new Stack<Socket>();

  /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      setContentView(R.layout.main);
      textView = (TextView)findViewById(R.id.textview);

      try {
        setupServer();
        setupCamera();
      } catch ( Exception ex ) {
        Log.e(LOG_TAG, "Error setting up :" + ex.getMessage());
      }
  }

  @Override
  protected void onPause() {
    super.onPause();
    Log.d(LOG_TAG, "No longer listening");
    isListening = false;
  }

  @Override
  protected void onResume() {
    super.onPause();
    Log.d(LOG_TAG, "Starting to listen");
    isListening = true;
  }

  /**
   * Sets up the TCP/IP server socket.
   */
  private void setupServer() throws IOException {
    Log.d(LOG_TAG, "Setting up server...");
    socket = new ServerSocket(SERVER_PORT);
    Log.i(LOG_TAG, "Listening on " + socket.getInetAddress().getHostAddress());
    listeningThread = new Thread(new Runnable() {
      public void run() {
        try {
          Log.d(LOG_TAG, "Started listening thread");
          while ( !isDead ) {
            if (isListening) {
              Socket client = socket.accept();
              clients.push(client);
              try {
                BufferedReader in = new BufferedReader(
                    new InputStreamReader(client.getInputStream()));
                String str = in.readLine();
                Log.d(LOG_TAG, "Received: " + str);
                if (str.equals("take_a_picture_dude")) {
                  takePicture();
                }
              } catch ( Exception ex ) {
                Log.e(
                    LOG_TAG, "Exception in socket thread: " + ex.getMessage());
              }
            }
            try {
              Thread.sleep(100);
            } catch ( Exception ignore ) {
            }
          }
        } catch(IOException ex) {
          Log.e(LOG_TAG, "Exception in socket thread: " + ex.getMessage());
        }
      }
    });
    listeningThread.start();
    Log.d(LOG_TAG, "Finished setting up server...");
  }

  /**
   * Connects to the camera and sets up the pictureCallback.
   */
  private void setupCamera() {
    camera = Camera.open();
    pictureCallback = new Camera.PictureCallback() {
      public void onPictureTaken(byte[] imageTaken, Camera camera) {
        Log.d(LOG_TAG, "Picture taken");
        try {
          while ( true ) {
            Socket client = clients.pop();
            try {
              client.getOutputStream().write(imageTaken);
              String adr =  client.getInetAddress().getHostAddress();
              Log.i(LOG_TAG, "Sending picture to " + adr);
              textView.setText("Sending picture to " + adr);
              client.close();
            } catch ( IOException ex ) {
              Log.e(LOG_TAG, "Error sending image data: " + ex.getMessage());
            }
          }
        } catch ( EmptyStackException ignore ) {
          // done
        }
      }
    };
  }

  /**
   * Takes a picture.
   */
  private void takePicture() {
    camera.takePicture(null, null, pictureCallback);
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();

    if (camera != null) {
      camera.release();
    }
    if (isListening && listeningThread.isAlive()) {
      isDead = true;
    }
  }

    
}
