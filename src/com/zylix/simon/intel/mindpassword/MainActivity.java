package com.zylix.simon.intel.mindpassword;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Timer;
import java.util.TimerTask;

import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.ActionBar;
import android.support.v4.app.Fragment;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnTouchListener;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;
import android.os.Build;
import android.preference.PreferenceManager;

import android.util.Log;

import com.neurosky.thinkgear.*;

import android.content.Intent;
import android.content.SharedPreferences;


public class MainActivity extends ActionBarActivity implements View.OnClickListener{
	private static final String ACTIVITY_TAG="EEG Event type";
	private static final int RESULT_SETTINGS = 1;
	
	private Button connect_button;
	private Button reset_button;
	private Button confirm_button;
	private TextView debug_tv;
	private TextView password_tv;
	private CheckBox[] check_boxes = new CheckBox[5];
	private RatingBar mind_input;
	
	BluetoothAdapter bluetoothAdapter;
	TGDevice tgDevice;
	final boolean rawEnabled = false;
	
	static final int status_attention = 1;
	static final int status_mediation = 3;
	static final int status_blink = 7;
	private int[] password_pattern = new int[5];
	
	private int poor_signal;
	private int attention;
	private int mediation;
	private int blink;
	
	private int current_input = -1;
	
	Timer timer;
	MyTimerTask myTimerTask;
	
	private WebView mWebView = null;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		if (savedInstanceState == null) {
			getSupportFragmentManager().beginTransaction()
					.add(R.id.container, new PlaceholderFragment()).commit();
		}
		
		check_boxes[0] = (CheckBox) findViewById(R.id.Password_check1);
		check_boxes[1] = (CheckBox) findViewById(R.id.Password_check2);
		check_boxes[2] = (CheckBox) findViewById(R.id.Password_check3);
		check_boxes[3] = (CheckBox) findViewById(R.id.Password_check4);
		check_boxes[4] = (CheckBox) findViewById(R.id.Password_check5);
		mind_input = (RatingBar)findViewById(R.id.password_inputbar);
		
		//Make rating bar non touchable
		mind_input.setOnTouchListener(new OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				return true;
				}
			});

		debug_tv = (TextView) findViewById(R.id.debug_textview);
		debug_tv.setText("");
		
		reset_button = (Button)findViewById(R.id.reset_button);
		confirm_button = (Button)findViewById(R.id.unlock_button);
		confirm_button.setEnabled(false);
		reset_button.setOnClickListener(this);
		confirm_button.setOnClickListener(this);
        
		//set default password pattern -- 3-3-3-3-3 : all attentions
        for(int i=0; i<4; i++)
        	password_pattern[i] = 3;
        
        //webview preload----------------------------------
        mWebView = (WebView)findViewById(R.id.webview);
		
		mWebView.setWebViewClient(mWebViewClient);
		mWebView.setWebChromeClient(mWebChromeClient);
        
        //-------------------------------------------------
		bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if(bluetoothAdapter == null) {
        	// Alert user that Bluetooth is not available
        	Toast.makeText(this, "Bluetooth not available", Toast.LENGTH_LONG).show();
        	finish();
        	return;
        }else {
    	/* create the TGDevice */
    	tgDevice = new TGDevice(bluetoothAdapter, handler);
        }

	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {

		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}


	/**
	 * A placeholder fragment containing a simple view.
	 */
	public static class PlaceholderFragment extends Fragment {

		public PlaceholderFragment() {
		}

		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container,
				Bundle savedInstanceState) {
			View rootView = inflater.inflate(R.layout.fragment_main, container,
					false);
			return rootView;
		}
	}
	
	
	//Settings handling code--------------------------
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			Intent i = new Intent(this, UserSettingActivity.class);
			startActivityForResult(i, RESULT_SETTINGS);
			return true;
		}
		return super.onOptionsItemSelected(item);
	}
		
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		switch (requestCode) {
		case RESULT_SETTINGS:
			showUserSettings();
			break;

		}

	}

    @Override
    protected void onDestroy(){ 
        super.onDestroy();
        timer.cancel();
        //Kill myself
        android.os.Process.killProcess(android.os.Process.myPid());
    }
    
	private String debug_password_map(int i){
		String ret;
		switch(i){
		case 1:
			ret = new String("Attention");
			break;
		case 7:
			ret = new String("Mediation");
			break;
		case 3:
			ret = new String("Blink");
			break;
		default:
			ret = new String("Unkonwn");	
		}
		return ret;		
	}
	
	private StringBuilder debug_passwords_map(int[] pass){
		StringBuilder builder = new StringBuilder();
		for(int i=0; i<4; i++){
			builder.append("\t" + debug_password_map(pass[i]));
		}		
		return builder;
	}
	
	private void showUserSettings() {
		SharedPreferences sharedPrefs = PreferenceManager
				.getDefaultSharedPreferences(this);

		StringBuilder builder = new StringBuilder();
		password_pattern[0] = Integer.valueOf(sharedPrefs.getString("password1", "NULL"));
		password_pattern[1] = Integer.valueOf(sharedPrefs.getString("password2", "NULL"));
		password_pattern[2] = Integer.valueOf(sharedPrefs.getString("password3", "NULL"));
		password_pattern[3] = Integer.valueOf(sharedPrefs.getString("password4", "NULL"));
		password_pattern[4] = Integer.valueOf(sharedPrefs.getString("password5", "NULL"));
		
		builder.append("\n Password1: "
				+ debug_password_map(password_pattern[0]));		
		builder.append("\n Password2: "
				+ debug_password_map(password_pattern[1]));		
		builder.append("\n Password3: "
				+ debug_password_map(password_pattern[2]));		
		builder.append("\n Password4: "
				+ debug_password_map(password_pattern[3]));		
		builder.append("\n Password5: "
				+ debug_password_map(password_pattern[4])+"\n");

		debug_tv.append(builder.toString());
		
	}

	/**
     * Handles messages from TGDevice
     */
    private final Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
        	switch (msg.what) {
            case TGDevice.MSG_STATE_CHANGE:

                switch (msg.arg1) {
	                case TGDevice.STATE_IDLE:
	                    break;
	                case TGDevice.STATE_CONNECTING:		                	
	                	debug_tv.append("Connecting...\n");
	                	break;		                    
	                case TGDevice.STATE_CONNECTED:
	                	debug_tv.append("Connected!\n");
	                	tgDevice.start();
	                    break;
	                case TGDevice.STATE_NOT_FOUND:
	                	debug_tv.append("Can't find\n");
	                	break;
	                case TGDevice.STATE_DISCONNECTED:
	                	debug_tv.append("Disconnected mang\n");
                }

                break;
            case TGDevice.MSG_POOR_SIGNAL:
            	poor_signal = msg.arg1;
            	Log.i(ACTIVITY_TAG, "PoorSignal: " + msg.arg1 + "\n");
            	
                break;
            case TGDevice.MSG_RAW_DATA:	  
            	//raw1 = msg.arg1;
            	//Log.i(ACTIVITY_TAG, "Got raw: " + msg.arg1 + "\n");
            	break;
            case TGDevice.MSG_HEART_RATE:
            	Log.i(ACTIVITY_TAG, "Heart rate: " + msg.arg1 + "\n");
                break;
            case TGDevice.MSG_ATTENTION:
            	attention = msg.arg1;
            	Log.i(ACTIVITY_TAG, "Attention: " + msg.arg1 + "\n");
            	break;
            case TGDevice.MSG_MEDITATION:
            	mediation = msg.arg1;
            	Log.i(ACTIVITY_TAG, "Mediation: " + msg.arg1 + "\n");
            	break;
            case TGDevice.MSG_BLINK:
            	blink = msg.arg1;
            	Log.i(ACTIVITY_TAG, "Blink: " + msg.arg1 + "\n");
            	break;
            case TGDevice.MSG_RAW_COUNT:
            		//Log.i(ACTIVITY_TAG, "Raw Count: " + msg.arg1 + "\n");
            	break;
            case TGDevice.MSG_LOW_BATTERY:
            	Toast.makeText(getApplicationContext(), "Low battery!", Toast.LENGTH_SHORT).show();
            	break;
            case TGDevice.MSG_RAW_MULTI:
            	//TGRawMulti rawM = (TGRawMulti)msg.obj;
            	//Log.i(ACTIVITY_TAG, "Raw1: " + rawM.ch1 + "\nRaw2: " + rawM.ch2);
            default:
            	break;
        }
        }
    };
    
    public void doStuff(View view) {
    	
    	if(tgDevice.getState() != TGDevice.STATE_CONNECTING && tgDevice.getState() != TGDevice.STATE_CONNECTED)
    		tgDevice.connect(rawEnabled);   
    	//tgDevice.ena
    }
    
	@Override
	public void onClick(View v) {
		switch (v.getId()) {
			case R.id.reset_button: 
				//Revert everything back!-------
				for(int i=0; i<5; i++)
					check_boxes[i].setChecked(false);
				mind_input.setRating((float)0.0);
				timer.cancel();
				current_input = -1;
				confirm_button.setEnabled(false);
				mWebView.loadUrl("http://192.168.15.234:1337/ledoff");
				//-------------------------------
				break;	
		
			case R.id.unlock_button: {
				mWebView.loadUrl("http://192.168.15.234:1337/ledon");
				break;
			}
		}		
	}
	
	//Checkbox handle function---------------------------------------
	public void onCheckboxClicked(View view) {
	    // Is the view now checked?
	    boolean checked = ((CheckBox) view).isChecked();
	    
	    // Check which checkbox was clicked
	    switch(view.getId()) {
	        case R.id.Password_check1:
	            if (checked){
	            	Toast.makeText(this, "Now detect Password 1 : " + debug_password_map(password_pattern[0]), Toast.LENGTH_SHORT).show();
	            	current_input = 0;
	            	attention = 0;
	            	mediation = 0;
	            	blink = 0;
	                timer = new Timer();
	                myTimerTask = new MyTimerTask();
	            	timer.schedule(myTimerTask, 1000, 3000);
	            }else{
	            	//Toast.makeText(this, "Uncheck Password 1", Toast.LENGTH_SHORT).show();
	            	timer.cancel();
	            	mind_input.setRating((float)0.0);
	            }
	            break;
	        case R.id.Password_check2:
	            if (checked){
	            	Toast.makeText(this, "Now detect Password 2 : " + debug_password_map(password_pattern[1]), Toast.LENGTH_SHORT).show();
	            	current_input = 1;	            	
	            	attention = 0;
	            	mediation = 0;
	            	blink = 0;
	                timer = new Timer();
	                myTimerTask = new MyTimerTask();
	            	timer.schedule(myTimerTask, 1000, 3000);
	            }else{
	            	//Toast.makeText(this, "Uncheck Password 2", Toast.LENGTH_SHORT).show();
	            	timer.cancel();
	            	mind_input.setRating((float)1.0);
	            }
	            break;
	        case R.id.Password_check3:
	            if (checked){
	            	Toast.makeText(this, "Now detect Password 3 : " + debug_password_map(password_pattern[2]), Toast.LENGTH_SHORT).show();
	            	current_input = 2;
	            	attention = 0;
	            	mediation = 0;
	            	blink = 0;
	                timer = new Timer();
	                myTimerTask = new MyTimerTask();
	            	timer.schedule(myTimerTask, 1000, 3000);
	            }else{
	            	//Toast.makeText(this, "Uncheck Password 3", Toast.LENGTH_SHORT).show();
	            	timer.cancel();
	            	mind_input.setRating((float)2.0);
	            }
	            break;
	        case R.id.Password_check4:
	            if (checked){
	            	Toast.makeText(this, "Now detect Password 4 : " + debug_password_map(password_pattern[3]), Toast.LENGTH_SHORT).show();
	            	current_input = 3;
	            	attention = 0;
	            	mediation = 0;
	            	blink = 0;
	                timer = new Timer();
	                myTimerTask = new MyTimerTask();
	            	timer.schedule(myTimerTask, 1000, 3000);
	            }else{
	            	//Toast.makeText(this, "Uncheck Password 4", Toast.LENGTH_SHORT).show();
	            	timer.cancel();
	            	mind_input.setRating((float)3.0);
	            }
	            break;
	        case R.id.Password_check5:
	            if (checked){
	            	Toast.makeText(this, "Now detect Password 5 : " + debug_password_map(password_pattern[4]), Toast.LENGTH_SHORT).show();
	            	current_input = 4;
	            	attention = 0;
	            	mediation = 0;
	            	blink = 0;
	                timer = new Timer();
	                myTimerTask = new MyTimerTask();
	            	timer.schedule(myTimerTask, 1000, 3000);
	            }else{
	            	//Toast.makeText(this, "Uncheck Password 5", Toast.LENGTH_SHORT).show();
	            	timer.cancel();
	            	mind_input.setRating((float)4.0);
	            }
	            break;
	        // TODO: Veggie sandwich
	    }
	}
	//---------------------------------------------------------------
	
	// Inner class -> Timer task to check password----------------------------------------
	class MyTimerTask extends TimerTask {
		
		private void check_status(){
			if(password_pattern[current_input] == 1){
				   if((poor_signal == 0) && (attention >= 45)) mind_input.setRating((float)current_input+1);
			   }else if(password_pattern[current_input] == 7){
				   if((poor_signal == 0) && (mediation >= 45)) mind_input.setRating((float)current_input+1);
			   }else if(password_pattern[current_input] == 3){
				   if((poor_signal == 0) && (blink >= 33)) mind_input.setRating((float)current_input+1);
			   }else{
				   Log.i(ACTIVITY_TAG, "Something wrong in checking password\n");
			   }

		}

		  @Override
		  public void run() {
	   
		   runOnUiThread(new Runnable(){
			   	   
			   @Override
			   public void run() {
				   switch(current_input){
				   case 0:
					   check_status();
					   break;
				   case 1:
					   check_status();
					   break;
				   case 2:
					   check_status();
					   break;
				   case 3:
					   check_status();
					   break;
				   case 4:
					   check_status();
					   break;
				   }
					if(current_input == 4)
						   confirm_button.setEnabled(true);
			   }
			   }
		   );
		   
		  }
	}

	//--------------------------------------------------------------------
	//Webview inner class-----------------------------------------------------

	WebViewClient mWebViewClient = new WebViewClient() {
		@Override
		public boolean shouldOverrideUrlLoading(WebView view, String url) {
			view.loadUrl(url);
			return true;
		}
	};
	

	WebChromeClient mWebChromeClient = new WebChromeClient() {

		@Override
		public void onReceivedTitle(WebView view, String title) {
			if ((title != null) && (title.trim().length() != 0)) {
				setTitle(title);
			}
		}
	};
	//------------------------------------------------------------------------
}
