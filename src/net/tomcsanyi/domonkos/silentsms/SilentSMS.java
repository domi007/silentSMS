package net.tomcsanyi.domonkos.silentsms;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.net.Socket;

import com.android.internal.telephony.IccSmsInterfaceManager;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.SMSDispatcher;
import net.tomcsanyi.domonkos.silentsms.R;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.ContactsContract.Contacts;
import android.telephony.SmsMessage;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.content.Intent;
import android.database.Cursor;

public class SilentSMS extends Activity {
	private Button btnSendSMS;
	private ImageButton btnContactPick;
	private EditText txtPhoneNo;
	private EditText txtMessage;
	private static final int CONTACT_PICKER_RESULT = 1001;
	private String TAG = "SilentSMS";
	private volatile boolean delivered = false;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		String DELIVERED = "SMS_DELIVERED";

		registerReceiver(new BroadcastReceiver() {
			@Override
			public void onReceive(Context arg0, Intent arg1) {
				switch (getResultCode()) {
				case Activity.RESULT_OK: {
					delivered = true;
					TextView tv = (TextView) findViewById(R.id.output);
					StringBuilder sb = new StringBuilder(tv.getText());
					sb.append("[INFO] SMS delivered");
					sb.append('\n');
					tv.setText(sb);
					// Toast.makeText(getApplicationContext(), "SMS delivered",
					// Toast.LENGTH_SHORT).show();
					break;
				}
				case Activity.RESULT_CANCELED: {
					TextView tv = (TextView) findViewById(R.id.output);
					StringBuilder sb = new StringBuilder(tv.getText());
					sb.append("[ERROR] SMS NOT delivered");
					sb.append('\n');
					tv.setText(sb);
					// Toast.makeText(getApplicationContext(),
					// "SMS not delivered", Toast.LENGTH_SHORT).show();
					break;
				}
				}
			}
		}, new IntentFilter(DELIVERED));

		btnSendSMS = (Button) findViewById(R.id.btnSendSMS);
		btnContactPick = (ImageButton) findViewById(R.id.btnContact);
		txtPhoneNo = (EditText) findViewById(R.id.txtPhoneNo);
		txtMessage = (EditText) findViewById(R.id.txtMessage);

		new NetworkService().execute();

		btnSendSMS.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				String phoneNo = txtPhoneNo.getText().toString();
				String message = txtMessage.getText().toString();
				if (phoneNo.length() > 0 && message.length() > 0) {
					if (!sendSMS(phoneNo, message)) {
						Toast.makeText(getBaseContext(),
								"An error occured while sending SMS.",
								Toast.LENGTH_SHORT).show();
					} else
						Toast.makeText(getBaseContext(), "SMS sent =)",
								Toast.LENGTH_SHORT).show();
				} else
					Toast.makeText(getBaseContext(),
							"Please enter both phone number and message.",
							Toast.LENGTH_SHORT).show();

			}
		});

		btnContactPick.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				Intent contactPickerIntent = new Intent(Intent.ACTION_PICK);
				contactPickerIntent.setData(Contacts.CONTENT_URI);
				contactPickerIntent
						.setType(android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE);
				startActivityForResult(contactPickerIntent,
						CONTACT_PICKER_RESULT);
			}
		});

	}

	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		Log.d("ZeroSMS", "Result: " + String.valueOf(resultCode));
		if (resultCode == -1) {
			switch (requestCode) {
			case CONTACT_PICKER_RESULT:
				Cursor cursor = null;
				try {
					Uri result = data.getData();
					Log.d("ZeroSMS", result.toString());
					cursor = getContentResolver()
							.query(result,
									null,
									android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER,
									null, null);
					if (cursor.moveToFirst())
						txtPhoneNo
								.setText(cursor.getString(cursor
										.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER)));

				} catch (Exception e) {
					Log.e("ZeroSMS", "An error occured while picking contact.");
				} finally {
					if (cursor != null) {
						cursor.close();
					}
				}
				break;
			}
		} else {
			// gracefully handle failure
			Log.w("ZeroSMS", "Warning: activity result not ok");
		}
	}

	public static String bytesToHex(byte[] bytes) {
		final char[] hexArray = { '0', '1', '2', '3', '4', '5', '6', '7', '8',
				'9', 'A', 'B', 'C', 'D', 'E', 'F' };
		char[] hexChars = new char[bytes.length * 2];
		int v;
		for (int j = 0; j < bytes.length; j++) {
			v = bytes[j] & 0xFF;
			hexChars[j * 2] = hexArray[v >>> 4];
			hexChars[j * 2 + 1] = hexArray[v & 0x0F];
		}
		return new String(hexChars);
	}

	/* Sends class 0 SMS */
	private boolean sendSMS(String phoneNumber, String message) {
		int size;
		Field f;
		delivered = false;
		String DELIVERED = "SMS_DELIVERED";

		Log.d(TAG, "Retrieving phone instance ...");
		Phone phone = PhoneFactory.getDefaultPhone();

		PendingIntent deliveredPI = PendingIntent.getBroadcast(
				phone.getContext(), 0, new Intent(DELIVERED), 0);

		/* Get IccSmsInterfaceManager */
		Log.d(TAG, "Retrieving SmsInterfaceManager ...");
		IccSmsInterfaceManager ismsm = phone.getIccSmsInterfaceManager();

		try {
			Log.d(TAG, "Retrieving mDispatcher ...");
			f = IccSmsInterfaceManager.class.getDeclaredField("mDispatcher");
			f.setAccessible(true);
			SMSDispatcher sms_disp = (SMSDispatcher) f.get(ismsm);

			Log.d(TAG, "Formatting class 0 SMS ...");
			byte[] b = new byte[0];
			SmsMessage.SubmitPdu pdus = SmsMessage.getSubmitPdu(null,
					phoneNumber, message, true);

			// For debug purposes you can uncomment these lines to show the PDU
			// before modification

			// TextView tv = (TextView) findViewById(R.id.output);
			// StringBuilder sb = new StringBuilder(tv.getText());
			// sb.append("[INFO] SMS-PDU created:\n");
			// sb.append(bytesToHex(pdus.encodedMessage));
			// sb.append('\n');
			// tv.setText(sb);

			/* modify SMS bytes */
			size = (int) pdus.encodedMessage[2];
			size = (size / 2) + (size % 2);
			/* ZeroSMS (flashSMS) - change class to Class 0 */
			// pdus.encodedMessage[size + 5] = (byte) 0xF0;
			/* instead of Class 0 we change this to PID 64 (silent SMS) */
			pdus.encodedMessage[size + 4] = (byte) 0x40;

			// For debug purposes you can uncomment these lines to show the PDU
			// after modification
			// TextView tv = (TextView) findViewById(R.id.output);
			// sb.append("SMS-PDU modified (Class 0):\n");
			// sb.append("[INFO] SMS-PDU modified (PID 64):\n");
			// sb.append(bytesToHex(pdus.encodedMessage));
			// sb.append('\n');
			// tv.setText(sb);

			/* send raw pdu */
			Log.d(TAG, "Sending SMS via sendRawPdu() ...");
			try {
				/* Android 2.2 -> 4.0.* */
				Method m = SMSDispatcher.class.getDeclaredMethod("sendRawPdu",
						b.getClass(), b.getClass(), PendingIntent.class,
						PendingIntent.class);
				m.setAccessible(true);
				m.invoke(sms_disp, pdus.encodedScAddress, pdus.encodedMessage,
						null, deliveredPI);
			} catch (NoSuchMethodException e) {
				/* Android 4.1.2 */
				Method m = SMSDispatcher.class.getDeclaredMethod("sendRawPdu",
						b.getClass(), b.getClass(), PendingIntent.class,
						PendingIntent.class, String.class);
				m.setAccessible(true);
				m.invoke(sms_disp, pdus.encodedScAddress, pdus.encodedMessage,
						null, deliveredPI, phoneNumber);
			}
			Log.d(TAG, "SMS sent");
			return true;

		} catch (SecurityException e) {
			Log.e(TAG, "Exception: Security !");
			e.printStackTrace();
			return false;
		} catch (NoSuchFieldException e) {
			Log.e(TAG, "Exception: Field mDispatcher not found !");
			e.printStackTrace();
			return false;
		} catch (IllegalArgumentException e) {
			Log.e(TAG, "Exception: Illegal Argument !");
			e.printStackTrace();
			return false;
		} catch (IllegalAccessException e) {
			Log.e(TAG, "Exception: Illegal access !");
			e.printStackTrace();
			return false;
		} catch (NoSuchMethodException e) {
			Log.e(TAG, "Exception: sendRawPdu() not found !");
			e.printStackTrace();
			return false;
		} catch (InvocationTargetException e) {
			Log.e(TAG, "Exception: cannot invoke sendRawPdu() !");
			e.printStackTrace();
			return false;
		}
	}

	// NetworkService

	protected class NetworkService extends AsyncTask<String, String, Void> {
		protected PrintWriter output;
		String DELIVERED = "SMS_DELIVERED";

		@Override
		protected Void doInBackground(String... phonenumber) {

			try {
				publishProgress("NetworkService starting up...");
				final ServerSocket ss = new ServerSocket(1337);
				publishProgress(getString(R.string.listening, ss.getLocalPort()));
				final Socket s = ss.accept();
				publishProgress(getString(R.string.connected, s
						.getRemoteSocketAddress().toString()));
				final BufferedReader input = new BufferedReader(
						new InputStreamReader(s.getInputStream(), "UTF-8"));
				output = new PrintWriter(new OutputStreamWriter(
						s.getOutputStream(), "UTF-8"));
				while (true) {
					output.print("SilentSMS> ");
					output.flush();
					if (delivered) {
						output.println("SMS delivered\n");
						output.flush();
						delivered = false;
					}
					final String cmd = input.readLine();
					if (cmd == null || cmd.equals("exit")) {
						output.print("Closing connection, goodbye.\n");
						output.flush();
						output.close();
						s.close();
						ss.close();
						return null;
					}
					processCommand(cmd);
				}
			} catch (IOException ioe) {
				publishProgress(getString(R.string.exception, ioe.toString()));
			}

			return null;
		}

		protected void processCommand(final String cmd) throws IOException {
			if (!cmd.matches("[0-9]+") && !cmd.equals(""))
				output.println("Usage: NUMBER \n NUMBER: the number you would like to send a silentSMS to (international format, no + sign)\n");
			else
				publishProgress(cmd);
		}

		// f*cking ugly code, but it needs to run on the Main - System.Phone
		// thread because of reflection, so sorry
		@Override
		protected void onProgressUpdate(String... progress) {
			super.onProgressUpdate(progress);
			TextView tv = (TextView) findViewById(R.id.output);
			StringBuilder sb = new StringBuilder(tv.getText());
			for (String s : progress) {
				sb.append(s);
				sb.append('\n');
			}
			tv.setText(sb);
			if (progress[0].matches("[0-9]+")) {
				Boolean res = sendSMS(progress[0], "silent");
				if (res) {
					output.println("SMS sent successfully\n");
					output.flush();
					publishProgress(getString(R.string.info,
							"SMS sent successfully"));
				} else {
					output.println("SMS not sent\n");
					output.flush();
					publishProgress(getString(R.string.error, "SMS not sent"));

				}
			}
		}
	}

}