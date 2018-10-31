package org.elastos.carrier.common;

import org.elastos.carrier.Carrier;
import org.elastos.carrier.exceptions.ElastosException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TestHelper {
	public interface ITestChannelExecutor {
		void executor();
	}

	public static boolean addFriendAnyway(Carrier carrier, RobotConnector robot,
										  TestContext context, Object syncher) {
		try {
			TestContext.Bundle bundle = context.getExtra();

			if (carrier.isFriend(robot.getNodeid())) {
				while(!bundle.getRobotOnline()) {
					Thread.sleep(0, 500 * 1000);
				}
				return true;
			}

			carrier.addFriend(robot.getAddress(), "auto-reply");

			// wait for friend_added callback invoked.
			synchronized (syncher) {
				syncher.wait();
			}

			// wait for friend_connection (online) callback invoked.
			synchronized (syncher) {
				syncher.wait();
			}

			// wait until robot being notified us connected.
			String[] args = robot.readAck();
			assertEquals(args.length, 2);
			assertEquals(args[0], "fadd");
			assertEquals(args[1], "succeeded");
		}
		catch (ElastosException | InterruptedException e) {
			e.printStackTrace();
			assertTrue(false);
			return false;
		}

		return true;
	}

	public static boolean removeFriendAnyway(Carrier carrier, RobotConnector robot,
											 TestContext context, Object syncher) {
		try {
			TestContext.Bundle bundle = context.getExtra();

			if (!carrier.isFriend(robot.getNodeid())) {
				while (bundle.getRobotOnline()) {
					Thread.sleep(0, 500 * 1000);
				}
				return true;
			} else {
				while (!bundle.getRobotOnline()) {
					//sleep 500 microseconds = 500 * 1000 nanoseconds
					Thread.sleep(0, 500 * 1000);
				}
			}

			carrier.removeFriend(robot.getNodeid());

			// wait for friend_removed callback invoked.
			synchronized (syncher) {
				syncher.wait();
			}

			assertTrue(robot.writeCmd(String.format("fremove %s", carrier.getUserId())));

			// wait for friend_connection (online -> offline) callback invoked.
			synchronized (syncher) {
				syncher.wait();
			}

			// wait for completion of robot "fremove" command.
			String[] args = robot.readAck();
			assertEquals(args.length, 2);
			assertEquals(args[0], "fremove");
			assertEquals(args[1], "succeeded");
		}
		catch (ElastosException | InterruptedException e) {
			e.printStackTrace();
			return false;
		}

		return true;
	}

	public static byte[] getBytes(char[] chars) {
		String tmp = new String(chars);
		return tmp.getBytes();
	}
}
