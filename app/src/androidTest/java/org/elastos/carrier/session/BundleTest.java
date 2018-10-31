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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class BundleTest {
	private static final String TAG = "BundleTest";
	private static TestContext context = new TestContext();
	private static TestHandler handler = new TestHandler(context);
	private static SessionManagerHandler sessionHandler = new SessionManagerHandler();
	private static RobotConnector robot;
	private static Carrier carrier;
	private static Manager sessionManager;

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
	}

	class TestStreamHandler implements StreamHandler {
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

	@Test
	public void testSessionWithBundle() {
		try {
			assertTrue(TestHelper.addFriendAnyway(carrier, robot, context, handler));
			assertTrue(carrier.isFriend(robot.getNodeid()));

			assertTrue(robot.writeCmd("sinit\n"));
			String[] args = robot.readAck();
			assertNotNull(args);
			assertTrue(args.length == 2);
			assertEquals("sinit", args[0]);
			assertEquals("success", args[1]);

			String userid = carrier.getUserId();
			String bundle = "session_bundle_test";
			assertTrue(robot.writeCmd(String.format("srequest %s %d %s\n", userid, 0, bundle)));

			args = robot.readAck();
			assertNotNull(args);
			assertTrue(args.length == 2);
			assertEquals("srequest", args[0]);
			assertEquals("success", args[1]);

			//for session request callback
			synchronized (sessionHandler) {
				sessionHandler.wait();
			}

			LocalData data = (LocalData)context.getExtra().getExtraData();
			assertTrue(data.mRequestReceived);
			assertTrue(data.mSdp != null && !data.mSdp.isEmpty());

			Session session = sessionManager.newSession(robot.getNodeid());
			assertTrue(session != null);

			TestStreamHandler streamHandler = new TestStreamHandler();
			Stream stream = session.addStream(StreamType.Text, 0, streamHandler);
			assertNotNull(stream);

			//Stream initialized
			synchronized (streamHandler) {
				streamHandler.wait();
			}
			data = (LocalData)context.getExtra().getExtraData();
			assertTrue(data.mState.equals(StreamState.Initialized));

			session.replyRequest(0, null);

			data = (LocalData)context.getExtra().getExtraData();
			assertTrue(data.mState.equals(StreamState.TransportReady));

			session.start(data.mSdp);

			//Stream connecting
			synchronized (streamHandler) {
				streamHandler.wait();
			}

			data = (LocalData)context.getExtra().getExtraData();
			if ((!data.mState.equals(StreamState.Connecting)) && (data.mState.equals(StreamState.Connected))) {
				// if error, consume ctrl acknowlege from robot.
				args = robot.readAck();
			}

			assertTrue(data.mState.equals(StreamState.Connecting));
			args = robot.readAck();
			assertNotNull(args);
			assertTrue(args.length == 2);
			assertEquals("sconnect", args[0]);
			assertEquals("success", args[1]);

			assertTrue(data.mState.equals(StreamState.Connected));

			session.removeStream(stream);
			session.close();
			robot.writeCmd("sfree\n");
		}
		catch (ElastosException e) {
			assertTrue(false);
		}
		catch (InterruptedException e) {
			e.printStackTrace();
		}
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
