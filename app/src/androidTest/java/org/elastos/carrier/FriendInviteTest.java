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
public class FriendInviteTest {
	private static final String TAG = "FriendInviteTest";

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

		@Override
		public void onFriendInviteRequest(Carrier carrier, String from, String data) {
			TestContext.Bundle bundle = mContext.getExtra();
			bundle.setFrom(from);
			LocalData localData = (LocalData)bundle.getExtraData();
			if (localData == null) {
				localData = new LocalData();
			}
			localData.data = data;
			bundle.setExtraData(localData);
			Log.d(TAG, String.format("Friend [%s] invite info [%s]", from, data));

			synchronized (this) {
				this.notify();
			}
		}
	}

	static class LocalData {
		public int status = 0;
		public String reason = null;
		public String data = null;
	}

	class InviteResposeHandler implements FriendInviteResponseHandler {
		final TestHandler mTestHandler;
		TestContext mContext;
		InviteResposeHandler(TestContext context, TestHandler handler) {
			mContext = context;
			mTestHandler = handler;
		}

		public void onReceived(String from, int status, String reason, String data) {
			TestContext.Bundle bundle = mContext.getExtra();
			LocalData localData = (LocalData)bundle.getExtraData();
			if (localData == null) {
				localData = new LocalData();
			}
			localData.status = status;
			localData.reason = reason;
			localData.data = data;
			bundle.setExtraData(localData);
			Log.d(TAG, String.format("onReceived from [%s], data is [%s]", from, data));

			synchronized (mTestHandler) {
				mTestHandler.notify();
			}
		}
	}

	@Test
	public void testFriendInviteConfirm() {
		try {
			assertTrue(TestHelper.addFriendAnyway(carrier, robot, context, handler));
			assertTrue((carrier.isFriend(robot.getNodeid())));
			String hello = "hello";
			InviteResposeHandler h = new InviteResposeHandler(context, handler);
			carrier.inviteFriend(robot.getNodeid(), hello, h);
			String[] args = robot.readAck();
			if (args == null || args.length != 2) {
				assertTrue(false);
			}
			assertEquals(args[0], "data");
			assertEquals(args[1], hello);

			String invite_rsp_data = "invitation-confirmed";
			assertTrue(robot.writeCmd(String.format("freplyinvite %s confirm %s", carrier.getUserId(),
				invite_rsp_data)));

			// wait for invite response callback invoked.
			synchronized (handler) {
				handler.wait();
			}

			TestContext.Bundle bundle = context.getExtra();
			LocalData localData = (LocalData) bundle.getExtraData();
			assertEquals(bundle.getFrom(), robot.getNodeid());
			assertEquals(localData.status, 0);
			assertTrue(localData.reason == null || localData.reason.isEmpty());
			assertEquals(localData.data, invite_rsp_data);
		}
		catch (ElastosException | InterruptedException e) {
			e.printStackTrace();
			assertTrue(false);
		}
	}

	@Test
	public void testFriendInviteReject() {
		try {
			assertTrue(TestHelper.addFriendAnyway(carrier, robot, context, handler));
			assertTrue((carrier.isFriend(robot.getNodeid())));

			String hello = "hello";
			InviteResposeHandler h = new InviteResposeHandler(context, handler);
			carrier.inviteFriend(robot.getNodeid(), hello, h);
			String[] args = robot.readAck();
			if (args == null || args.length != 2) {
				assertTrue(false);
			}
			assertEquals(args[0], "data");
			assertEquals(args[1], hello);

			String reason = "unknown-error";
			assertTrue(robot.writeCmd(String.format("freplyinvite %s refuse %s", carrier.getUserId(),
				reason)));

			// wait for invite response callback invoked.
			synchronized (handler) {
				handler.wait();
			}

			TestContext.Bundle bundle = context.getExtra();
			LocalData localData = (LocalData) bundle.getExtraData();
			assertEquals(bundle.getFrom(), robot.getNodeid());
			assertTrue(localData.status != 0);
			assertEquals(localData.reason, reason);
			assertTrue(localData.data == null || localData.data.isEmpty());
		}
		catch (ElastosException | InterruptedException e) {
			e.printStackTrace();
			assertTrue(false);
		}
	}

	@Test
	public void testFriendInviteStranger() {
		try {
			assertTrue(TestHelper.removeFriendAnyway(carrier, robot, context, handler));
			assertFalse((carrier.isFriend(robot.getNodeid())));

			String hello = "hello";
			InviteResposeHandler h = new InviteResposeHandler(context, handler);
			carrier.inviteFriend(robot.getNodeid(), hello, h);
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
	public void testFriendInviteSelf() {
		try {
			String hello = "hello";
			InviteResposeHandler h = new InviteResposeHandler(context, handler);
			carrier.inviteFriend(carrier.getUserId(), hello, h);
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
