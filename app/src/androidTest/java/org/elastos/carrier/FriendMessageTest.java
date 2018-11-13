package org.elastos.carrier;

import android.util.Log;

import org.elastos.carrier.common.RobotConnector;
import org.elastos.carrier.common.TestContext;
import org.elastos.carrier.common.TestHelper;
import org.elastos.carrier.common.TestOptions;
import org.elastos.carrier.exceptions.ElastosException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

@RunWith(JUnit4.class)
public class FriendMessageTest {
	private static final String TAG = "FriendMessageTest";
	private static TestContext context = new TestContext();
	private static final TestHandler handler = new TestHandler(context);
	private static RobotConnector robot;
	private static Carrier carrier;

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

		@Override
		public void onFriendMessage(Carrier carrier, String from, byte[] message) {
			TestContext.Bundle bundle = mContext.getExtra();
			bundle.setFrom(from);
			bundle.setExtraData(getActualValue(message));

			Log.d(TAG, String.format("Friend message %s ", from));
			synchronized (this) {
				notify();
			}
		}
	}

	private static String getActualValue(byte[] data) {
		//The string from robot has '\n', delete it.
		byte[] newArray = new byte[data.length - 1];
		System.arraycopy(data, 0, newArray, 0, data.length - 1);
		return new String(newArray);
	}

	@Test
	public void testSendMessageToFriend() {
		assertTrue(TestHelper.addFriendAnyway(carrier, robot, context, handler));

		context.reset();

		try {
			assertTrue(carrier.isFriend(robot.getNodeid()));
			String out = "message-test";

			carrier.sendFriendMessage(robot.getNodeid(), out);

			String[] args = robot.readAck();
			assertTrue(args != null && args.length == 1);
			assertEquals(out, getActualValue(args[0].getBytes()));
		}
		catch (ElastosException e) {
			e.printStackTrace();
			assertTrue(false);
		}
	}

	@Test
	public void testReceiveMessageFromFriend() {
		try {
			assertTrue(TestHelper.addFriendAnyway(carrier, robot, context, handler));
			assertTrue(carrier.isFriend(robot.getNodeid()));

			String msg = "message-test";
			assertTrue(robot.writeCmd(String.format("fmsg %s %s", carrier.getUserId(), msg)));

			// wait for message from robot.
			synchronized (handler) {
				handler.wait();
			}

			TestContext.Bundle bundle = context.getExtra();
			assertEquals(robot.getNodeid(), bundle.getFrom());
			assertEquals(msg, bundle.getExtraData().toString());
		}
		catch (ElastosException | InterruptedException e) {
			e.printStackTrace();
			assertTrue(false);
		}
	}

	@Test
	public void testSendMessageToStranger() {
		try {
			TestHelper.removeFriendAnyway(carrier, robot, context, handler);
			assertFalse(carrier.isFriend(robot.getNodeid()));
			String msg = "test-message";
			carrier.sendFriendMessage(robot.getNodeid(), msg);
		}
		catch (ElastosException e) {
			e.printStackTrace();
			assertEquals(e.getErrorCode(), 0x8100000A);
		}
	}

	@Test
	public void testSendMessageToSelf() {
		try {
			String msg = "test-message";
			carrier.sendFriendMessage(carrier.getUserId(), msg);
		}
		catch (ElastosException e) {
			e.printStackTrace();
			assertEquals(e.getErrorCode(), 0x81000001);
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
		}
		catch (ElastosException | InterruptedException e) {
			e.printStackTrace();
			Log.e(TAG, "Carrier node start failed, abort this test.");
		}
	}

	@AfterClass
	public static void tearDown() {
		carrier.kill();
	}
}
