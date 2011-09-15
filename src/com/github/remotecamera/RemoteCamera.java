package com.github.remotecamera;

import android.app.Activity;
import android.os.Bundle;
import android.view.SurfaceHolder;

public class RemoteCamera extends Activity implements SurfaceHolder.Callback {
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
    }

  public void surfaceCreated(SurfaceHolder arg0) {
  }

  public void surfaceChanged(SurfaceHolder arg0, int arg1, int arg2, int arg3) {
  }

  public void surfaceDestroyed(SurfaceHolder arg0) {
  }
}
