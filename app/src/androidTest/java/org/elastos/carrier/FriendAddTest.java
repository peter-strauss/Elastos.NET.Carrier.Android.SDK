package org.elastos.carrier;


import android.support.test.runner.AndroidJUnit4;
import android.util.Log;

import org.elastos.carrier.common.RobotConnector;
import org.elastos.carrier.common.Synchronizer;
import org.elastos.carrier.common.TestContext;
import org.elastos.carrier.common.TestHelper;
import org.elastos.carrier.common.TestOptions;
import org.elastos.carrier.exceptions.ElastosException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

@RunWith(AndroidJUnit4.class)
public class FriendAddTest {
	private static final String TAG = "FriendAddTest";
	private static Synchronizer syncher = new Synchronizer();
	private static TestContext context = new TestContext();
	private static TestHandler handler = new TestHandler(syncher, context);
	private static RobotConnector robot;
	private static Carrier carrier;

	static class TestHandler extends AbstractCarrierHandler {
		private Synchronizer mSyncher;
		private TestContext mContext;

		TestHandler(Synchronizer syncher, TestContext context) {
			mSyncher = syncher;
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
			mContext.setExtra(bundle);

			Log.d(TAG, "Robot connection status changed -> " + status.toString());
			mSyncher.wakeup();
		}

		@Override
		public void onFriendRequest(Carrier carrier, String userId, UserInfo info, String hello) {
			TestContext.Bundle bundle = mContext.getExtra();
			bundle.setFrom(userId);
			bundle.setUserInfo(info);
			bundle.setHello(hello);
			mContext.setExtra(bundle);

			Log.d(TAG, "Received riend reqeust from " + userId + " with hello: " + hello);
			mSyncher.wakeup();
		}

		@Override
		public void onFriendAdded(Carrier carrier, FriendInfo info) {
			mSyncher.wakeup();
			Log.d(TAG, String.format("Friend %s added", info.getUserId()));
		}

		@Override
		public void onFriendRemoved(Carrier carrier, String friendId) {
			mSyncher.wakeup();
			Log.d(TAG, String.format("Friend %s removed", friendId));
		}
	}

	@Ignore
	public void testAddFriend() {
		try {
			context.reset();
			syncher.reset();

			TestHelper.removeFriendAnyway(carrier, robot, context, syncher);
			assertFalse(carrier.isFriend(robot.getNodeid()));

			String hello = "hello";
			carrier.addFriend(robot.getAddress(), hello);

			// wait for friend_added() callback to be invoked.
			syncher.await();
			String[] ackValues = robot.readAck();
			assertEquals(2, ackValues.length);
			assertEquals("hello", ackValues[0]);
			assertEquals(hello, ackValues[1]);

			assertTrue(robot.writeCmd("faccept " + carrier.getNodeId()));
			assertTrue(carrier.isFriend(robot.getNodeid()));

			// wait for friend connection (online) callback to be invoked.
			syncher.await();

			assertEquals(robot.getNodeid(), context.getExtra().getFrom());
			Log.i(TAG, "status: " + context.getExtra().getRobotConnectionStatus());
			//assertEquals(ConnectionStatus.Connected, context.getExtra().getRobotConnectionStatus());

			ackValues = robot.readAck();
			assertEquals(2, ackValues.length);
			assertEquals("fadd", ackValues[0]);
			assertEquals("succeeded", ackValues[1]);
			Log.d(TAG, "============================7");
		}
		catch (ElastosException e) {
			e.printStackTrace();
			fail();
		}
	}

	@Test
	public void testAcceptFriend() {
		try {
			context.reset();
			syncher.reset();

			TestHelper.removeFriendAnyway(carrier, robot, context, syncher);
			assertFalse(carrier.isFriend(robot.getNodeid()));

			String hello = "hello";
			boolean result;

			result = robot.writeCmd(String.format("fadd %s %s %s", carrier.getUserId(),
					carrier.getAddress(), hello));
			assertTrue(result);

			// wait for friend_request callback invoked;
			syncher.await();

			assertEquals(robot.getNodeid(), context.getExtra().getFrom());
			//assertEquals(context.getExtra().getFrom(), context.getExtra().getUserInfo().getUserId());
			assertEquals(hello, context.getExtra().getHello());

			carrier.acceptFriend(robot.getNodeid());

			// wait for friend added callback invoked;
			syncher.await();
			assertTrue(carrier.isFriend(robot.getNodeid()));

			// wait for friend connection (online) callback invoked.
			syncher.await();
			assertEquals(ConnectionStatus.Connected, context.getExtra().getRobotConnectionStatus());

			String[] ackValues = robot.readAck();
			assertEquals(2, ackValues.length);
			assertEquals("fadd", ackValues[0]);
			assertEquals("succeeded", ackValues[1]);
		} catch (ElastosException e) {
			e.printStackTrace();
			fail();
		}
	}

	@Test
	public void testAddFriendBeFriend() {
		try {
			carrier.isFriend(robot.getNodeid());
			carrier.addFriend(robot.getAddress(), "hello");
		}
		catch (ElastosException e) {
			e.printStackTrace();
			assertEquals(0x8100000B, e.getErrorCode());
		}
		catch (Exception e) {
			e.printStackTrace();
			fail();
		}
	}

	@Test
	public void testAddSelfBeFriend() {
		try {
			carrier.addFriend(carrier.getAddress(), "hello");
		}
		catch (ElastosException e) {
			assertEquals(0x81000001, e.getErrorCode());
		}
		catch (Exception e) {
			e.printStackTrace();
			fail();
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
			if (carrier.isFriend(robot.getNodeid()))
				syncher.await();
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
