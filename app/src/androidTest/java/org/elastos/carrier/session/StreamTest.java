package org.elastos.carrier.session;

import android.support.test.runner.AndroidJUnit4;
import android.util.Log;

import org.elastos.carrier.AbstractCarrierHandler;
import org.elastos.carrier.Carrier;
import org.elastos.carrier.ConnectionStatus;
import org.elastos.carrier.FriendInfo;
import org.elastos.carrier.common.RobotConnector;
import org.elastos.carrier.common.TestContext;
import org.elastos.carrier.common.TestHelper;
import org.elastos.carrier.common.TestOptions;
import org.elastos.carrier.exceptions.ElastosException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class StreamTest {
	private static final String TAG = "StreamTest";
	private static TestContext context = new TestContext();
	private static TestHandler handler = new TestHandler(context);
	private static RobotConnector robot;
	private static Carrier carrier;
	private static Manager sessionManager;
	private static final SessionManagerHandler sessionHandler = new SessionManagerHandler();
	private static final TestStreamHandler streamHandler = new TestStreamHandler();
	private static Session session;
	private static Stream stream;

	static class TestHandler extends AbstractCarrierHandler {
		private TestContext mContext;

		TestHandler(TestContext context) {
			mContext = context;
		}

		@Override
		public void onReady(Carrier carrier) {
			synchronized (carrier) {
				carrier.notify();
			}
		}

		@Override
		public void onFriendConnection(Carrier carrier, String friendId, ConnectionStatus status) {
			TestContext.Bundle bundle = mContext.getExtra();
			bundle.setRobotOnline(status == ConnectionStatus.Connected);
			bundle.setRobotConnectionStatus(status);
			bundle.setFrom(friendId);

			Log.d(TAG, "Robot connection status changed -> " + status.toString());
			synchronized (this) {
				notify();
			}
		}

		@Override
		public void onFriendAdded(Carrier carrier, FriendInfo info) {
			Log.d(TAG, String.format("Friend %s added", info.getUserId()));
			synchronized (this) {
				notify();
			}
		}

		@Override
		public void onFriendRemoved(Carrier carrier, String friendId) {
			Log.d(TAG, String.format("Friend %s removed", friendId));
			synchronized (this) {
				notify();
			}
		}
	}

	static class SessionManagerHandler implements ManagerHandler {
		@Override
		public void onSessionRequest(Carrier carrier, String from, String sdp) {
			LocalData data = (LocalData)context.getExtra().getExtraData();
			if (data == null) {
				data = new LocalData();
				context.getExtra().setExtraData(data);
			}
			data.mRequestReceived = true;
			data.mSdp = sdp;

			Log.d(TAG, String.format("Session Request from %s", from));
			synchronized (sessionHandler) {
				sessionHandler.notify();
			}
		}
	}

	static class LocalData {
		public String mSdp = null;
		public boolean mRequestReceived = false;
		public StreamState mState;
		public int mCompleteStatus = 0;
	}

	static class TestStreamHandler implements StreamHandler {
		@Override
		public void onStateChanged(Stream stream, StreamState state) {
			LocalData data = (LocalData)context.getExtra().getExtraData();
			if (data == null) {
				data = new LocalData();
				context.getExtra().setExtraData(data);
			}
			data.mState = state;

			Log.d(TAG, "onStateChanged state="+state);
			synchronized (this) {
				this.notify();
			}
		}

		@Override
		public void onStreamData(Stream stream, byte[] data) {
			Log.d(TAG, "onStreamData data="+(new String(data)));
			synchronized (this) {
				this.notify();
			}
		}

		@Override
		public boolean onChannelOpen(Stream stream, int channel, String cookie) {
			Log.d(TAG, "onChannelOpen cookie="+cookie);
			synchronized (this) {
				this.notify();
			}
			return true;
		}

		@Override
		public void onChannelOpened(Stream stream, int channel) {
			Log.d(TAG, "onChannelOpened channel="+channel);
			synchronized (this) {
				this.notify();
			}
		}

		@Override
		public void onChannelClose(Stream stream, int channel, CloseReason reason) {
			Log.d(TAG, "onChannelClose channel="+channel);
			synchronized (this) {
				this.notify();
			}
		}

		@Override
		public boolean onChannelData(Stream stream, int channel, byte[] data) {
			Log.d(TAG, "onChannelData channel="+channel+", data="+(new String(data)));
			synchronized (this) {
				this.notify();
			}
			return true;
		}

		@Override
		public void onChannelPending(Stream stream, int channel) {
			Log.d(TAG, "onChannelPending channel="+channel);
			synchronized (this) {
				this.notify();
			}
		}

		@Override
		public void onChannelResume(Stream stream, int channel) {
			Log.d(TAG, "onChannelResume channel="+channel);
			synchronized (this) {
				this.notify();
			}
		}
	}

	static class TestSessionRequestCompleteHandler implements SessionRequestCompleteHandler {
		@Override
		public void onCompletion(Session session, int status, String reason, String sdp) {
			Log.d(TAG, String.format("Session complete, status: %d, reason: %s", status, reason));
			LocalData data = (LocalData)context.getExtra().getExtraData();
			data.mCompleteStatus = status;

			if (status == 0) {
				try {
					session.start(sdp);
				}
				catch (ElastosException e) {
					e.printStackTrace();
					assertTrue(false);
				}
			}

			synchronized (this) {
				notify();
			}
		}
	}

	private static int sReturnValue = 0;
	private static void doBulkWrite()
	{
		final int MIN_DATA_SIZE = 1024*1024*10;
		final int MAX_DATA_SIZE = 1024*1024*100;

		final int MIN_PACKET_SIZE = 1024;
		final int MAX_PACKET_SIZE = 2048;

		//beginning to write message
		Random random = new Random();
		int send_size = random.nextInt(MAX_DATA_SIZE - MIN_DATA_SIZE) + MIN_DATA_SIZE;
		final int packet_size = random.nextInt(MAX_PACKET_SIZE - MIN_PACKET_SIZE) + MIN_PACKET_SIZE;
		final int packet_count = send_size / packet_size + 1;
		sReturnValue = -1;

		Thread thread = new Thread(new Runnable() {
			@Override
			public void run() {
				long duration;
				float speed;

				char[] packet = new char[packet_size];
				for (int j = 0; j < packet_size; j++) {
					packet[j] = 'D';
				}

				Log.d(TAG,"Begin sending data...");
				Log.d(TAG, String.format("Stream send: total %d packets and %d bytes per packet."
						, packet_count, packet_size));

				int rc = 0;

				byte[] charBytes = TestHelper.getBytes(packet);
				long start = System.currentTimeMillis();
				for (int i = 0; i < packet_count; i++) {
					int total = packet_size;
					int sent = 0;

					do {
						try {
							rc = stream.writeData(charBytes, sent, total - sent);
						}
						catch (ElastosException e) {
							e.printStackTrace();
							int errorCode = e.getErrorCode();
							if (errorCode == 0x81000010) {
								try {
									Thread.sleep(100);
								}
								catch (InterruptedException ie) {
									ie.printStackTrace();
								}

								continue;
							}
							else {
								Log.d(TAG, String.format("Write data failed: %s.", e.getErrorCode()));
								return;
							}
						}

						sent += rc;
					} while (sent < total);

//					if (i % 1000 == 0)
//						vlogD(".");
				}
				long end = System.currentTimeMillis();

				duration = end - start;
				speed = (((float)(packet_size * packet_count) / duration) * 1000) / 1024;

				Log.d(TAG, "Finished writing");
				Log.d(TAG, String.format("Total %d bytes in %d.%d seconds. %.2f KB/s",
						(packet_size * packet_count),
						(int)(duration / 1000), (int)(duration % 1000), speed));

				sReturnValue = 0;
			}
		});
		thread.start();

		try {
			thread.join();
		}
		catch (InterruptedException e) {
			e.printStackTrace();
		}

		assertEquals(0, sReturnValue);
	}

	private void testStreamWrite(int stream_options)
	{
		testStreamScheme(StreamType.Text, stream_options);
	}

	public void testStreamScheme(StreamType stream_type, int stream_options)
	{
		assertTrue(TestHelper.addFriendAnyway(carrier, robot, context, handler));
		try {
			assertTrue(carrier.isFriend(robot.getNodeid()));
			assertTrue(robot.writeCmd("sinit"));

			String[] args = robot.readAck();
			assertTrue(args != null && args.length == 2);
			assertEquals("sinit", args[0]);
			assertEquals("success", args[1]);

			session = sessionManager.newSession(robot.getNodeid());
			assertTrue(session != null);

			stream = session.addStream(stream_type, stream_options, streamHandler);
			assertNotNull(stream);

			//Stream initialized
			synchronized (streamHandler) {
				streamHandler.wait();
			}

			LocalData data = (LocalData)context.getExtra().getExtraData();
			data = (LocalData)context.getExtra().getExtraData();
			assertTrue(data.mState.equals(StreamState.Initialized));

			TestSessionRequestCompleteHandler completeHandler = new TestSessionRequestCompleteHandler();
			session.request(completeHandler);

			//Stream initialized
			synchronized (streamHandler) {
				streamHandler.wait(1000);
			}
			data = (LocalData)context.getExtra().getExtraData();
			assertTrue(data.mState.equals(StreamState.TransportReady));

			args = robot.readAck();
			assertTrue(args != null && args.length == 2);
			assertEquals("srequest", args[0]);
			assertEquals("received", args[1]);

			assertTrue(robot.writeCmd(String.format("sreply confirm %d %d", stream_type.value()
							, stream_options)));

			//Stream initialized
			synchronized (completeHandler) {
				completeHandler.wait();
			}

			data = (LocalData)context.getExtra().getExtraData();
			assertTrue(data.mCompleteStatus == 0);

			args = robot.readAck();
			assertTrue(args != null && args.length == 2);
			assertEquals("sreply", args[0]);
			assertEquals("success", args[1]);

			//Stream connecting
			synchronized (streamHandler) {
				streamHandler.wait(1000);
			}

			data = (LocalData)context.getExtra().getExtraData();
			if ((!data.mState.equals(StreamState.Connecting))
							&& (!data.mState.equals(StreamState.Connected))) {
				// if error, consume ctrl acknowlege from robot.
				args = robot.readAck();
				assertEquals("sconnect", args[0]);
				assertEquals("failed", args[1]);

			}

			// Stream 'connecting' state is a transient state.
			assertTrue(data.mState == StreamState.Connecting
							|| data.mState == StreamState.Connected);
			args = robot.readAck();
			assertTrue(args != null && args.length == 2);
			assertEquals("sconnect", args[0]);
			assertEquals("success", args[1]);

			//Stream connected
			synchronized (streamHandler) {
				streamHandler.wait(1000);
			}

			assertTrue(data.mState.equals(StreamState.Connected));

			doBulkWrite();

			session.removeStream(stream);
			session.close();

			data = (LocalData)context.getExtra().getExtraData();
			assertTrue(data.mState.equals(StreamState.Closed));

			robot.writeCmd("sfree");
		}
		catch (ElastosException | InterruptedException e) {
			e.printStackTrace();
		}
	}

	@Test
	public void testStream() {
		testStreamWrite(0);
	}

	@Test
	public void testStreamPlain() {
		testStreamWrite(Stream.PROPERTY_PLAIN);
	}

	@Test
	public void testStreamReliable() {
		testStreamWrite(Stream.PROPERTY_RELIABLE);
	}

	@Test
	public void testStreamReliablePlain() {
		int stream_options = 0;

		stream_options |= Stream.PROPERTY_PLAIN;
		stream_options |= Stream.PROPERTY_RELIABLE;

		testStreamWrite(stream_options);
	}

	@Test
	public void testStreamMultiplexing() {
		int stream_options = 0;

		stream_options |= Stream.PROPERTY_MULTIPLEXING;

		testStreamWrite(stream_options);
	}

	@Test
	public void testStreamPlainMultiplexing() {
		int stream_options = 0;

		stream_options |= Stream.PROPERTY_PLAIN;
		stream_options |= Stream.PROPERTY_MULTIPLEXING;

		testStreamWrite(stream_options);
	}

	@Test
	public void testStreamReliableMultiplexing() {
		int stream_options = 0;

		stream_options |= Stream.PROPERTY_RELIABLE;
		stream_options |= Stream.PROPERTY_MULTIPLEXING;

		testStreamWrite(stream_options);
	}

	@Test
	public void testStreamReliablePlainMultiplexing() {
		int stream_options = 0;

		stream_options |= Stream.PROPERTY_PLAIN;
		stream_options |= Stream.PROPERTY_RELIABLE;
		stream_options |= Stream.PROPERTY_MULTIPLEXING;

		testStreamWrite(stream_options);
	}

	@Test
	public void testStreamReliablePortforwarding() {
		int stream_options = 0;

		stream_options |= Stream.PROPERTY_RELIABLE;
		stream_options |= Stream.PROPERTY_MULTIPLEXING;
		stream_options |= Stream.PROPERTY_PORT_FORWARDING;

		testStreamWrite(stream_options);
	}

	@Test
	public void testStreamReliablePlainPortforwarding() {
		int stream_options = 0;

		stream_options |= Stream.PROPERTY_PLAIN;
		stream_options |= Stream.PROPERTY_RELIABLE;
		stream_options |= Stream.PROPERTY_MULTIPLEXING;
		stream_options |= Stream.PROPERTY_PORT_FORWARDING;

		testStreamWrite(stream_options);
	}

	@BeforeClass
	public static void setUp() {
		robot = RobotConnector.getInstance();
		TestOptions options = new TestOptions(context.getAppPath());

		try {
			Carrier.initializeInstance(options, handler);
			carrier = Carrier.getInstance();
			carrier.start(0);
			synchronized (carrier) {
				carrier.wait();
			}
			Log.i(TAG, "Carrier node is ready now");

			sessionManager = Manager.getInstance(carrier, sessionHandler);
			assertTrue(sessionManager != null);
		}
		catch (ElastosException | InterruptedException e) {
			e.printStackTrace();
			Log.e(TAG, "Carrier node start failed, abort this test.");
		}
	}

	@AfterClass
	public static void tearDown() {
		carrier.kill();
		sessionManager.cleanup();
	}
}
