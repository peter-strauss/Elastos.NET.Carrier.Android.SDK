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
public class FriendLabelTest {
	private static final String TAG = "FriendInviteTest";

	private static TestContext context = new TestContext();
	private static TestHandler handler = new TestHandler(context);
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
				this.notify();
			}
		}

		@Override
		public void onFriendAdded(Carrier carrier, FriendInfo info) {
			Log.d(TAG, String.format("Friend %s added", info.getUserId()));
			synchronized (this) {
				this.notify();
			}
		}

		@Override
		public void onFriendRemoved(Carrier carrier, String friendId) {
			Log.d(TAG, String.format("Friend %s removed", friendId));
			synchronized (this) {
				this.notify();
			}
		}
	}

	@Test
	public void testSetFriendLabel() {
		try {
			assertTrue(TestHelper.addFriendAnyway(carrier, robot, context, handler));
			assertTrue((carrier.isFriend(robot.getNodeid())));

			String label = "test_robot";
			carrier.labelFriend(robot.getNodeid(), label);
			FriendInfo info = carrier.getFriend(robot.getNodeid());
			assertEquals(info.getLabel(), label);
		}
		catch (ElastosException e) {
			e.printStackTrace();
			assertTrue(false);
		}
	}

	@Test
	public void testSetStrangerLabel() {
		try {
			assertTrue(TestHelper.removeFriendAnyway(carrier, robot, context, handler));
			assertFalse((carrier.isFriend(robot.getNodeid())));

			String label = "test_robot";
			carrier.labelFriend(robot.getNodeid(), label);
		}
		catch (ElastosException e) {
			e.printStackTrace();
			assertEquals(e.getErrorCode(), 0x8100000A);
		}
		catch (Exception e) {
			e.printStackTrace();
			assertTrue(false);
		}
	}

	@Test
	public void testSetSelfLabel() {
		try {
			String label = "test_robot";
			carrier.labelFriend(carrier.getUserId(), label);
		}
		catch (ElastosException e) {
			e.printStackTrace();
			assertEquals(e.getErrorCode(), 0x8100000A);
		}
		catch (Exception e) {
			e.printStackTrace();
			assertTrue(false);
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
