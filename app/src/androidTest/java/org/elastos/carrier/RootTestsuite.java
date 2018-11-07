package org.elastos.carrier;

import org.elastos.carrier.common.RobotConnector;
import org.elastos.carrier.session.ChannelTest;
import org.elastos.carrier.session.ManagerTest;
import org.elastos.carrier.session.NewTest;
import org.elastos.carrier.session.PortforwardingTest;
import org.elastos.carrier.session.RequestReplyTest;
import org.elastos.carrier.session.StreamTest;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

import static org.junit.Assert.fail;

@RunWith(Suite.class)
@Suite.SuiteClasses({
		//Carrier
		GetInstanceTest.class,
		NodeLoginTest.class,
		CheckIDTest.class,
		FriendAddTest.class, //TODO Ignore "testAddFriend" case
		FriendInviteTest.class,
		FriendLabelTest.class,
		FriendMessageTest.class,
		GetIDTest.class,
		GetInfoTest.class,

		//Session
		ManagerTest.class,
		NewTest.class,
		RequestReplyTest.class,
		StreamTest.class,
		PortforwardingTest.class,
		ChannelTest.class
})
public class RootTestsuite {
	private static String TAG = "RootTestsuite";
	private static RobotConnector robot;

	@BeforeClass
	public static void setup() {
		Log.d(TAG, "Carrier [setup]");
		robot = RobotConnector.getInstance();
		if (!robot.connectToRobot()) {
			android.util.Log.e(TAG, "Connection to robot failed, abort this test");
			fail();
		}
	}

	@AfterClass
	public static void teardown() {
		Log.d(TAG, "Carrier [teardown]");
		robot.disconnect();
	}
}
