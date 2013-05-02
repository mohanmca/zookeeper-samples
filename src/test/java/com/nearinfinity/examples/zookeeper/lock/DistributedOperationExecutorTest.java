package com.nearinfinity.examples.zookeeper.lock;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.nearinfinity.examples.zookeeper.util.ConnectionHelper;
import com.nearinfinity.examples.zookeeper.util.EmbeddedZooKeeperServer;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

public class DistributedOperationExecutorTest {

    private static EmbeddedZooKeeperServer embeddedServer;
    private ZooKeeper zooKeeper;
    private String testLockPath;
    private DistributedOperationExecutor executor;

    private static final int ZK_PORT = 53181;
    private static final String ZK_CONNECTION_STRING = "localhost:" + ZK_PORT;

    @BeforeClass
    public static void beforeAll() throws IOException, InterruptedException {
        embeddedServer = new EmbeddedZooKeeperServer(ZK_PORT);
        embeddedServer.start();
    }

    @AfterClass
    public static void afterAll() {
        embeddedServer.shutdown();
    }

    @Before
    public void setUp() throws IOException, InterruptedException {
        zooKeeper = new ConnectionHelper().connect(ZK_CONNECTION_STRING);
        testLockPath = "/test-write-lock-" + System.currentTimeMillis();
        executor = new DistributedOperationExecutor(zooKeeper);
    }

    @After
    public void tearDown() throws InterruptedException, KeeperException {
        if (zooKeeper.exists(testLockPath, false) == null) {
            return;
        }

        List<String> children = zooKeeper.getChildren(testLockPath, false);
        for (String child : children) {
            zooKeeper.delete(testLockPath + "/" + child, -1);
        }
        zooKeeper.delete(testLockPath, -1);
    }

    @Test
    public void testWithLock() throws InterruptedException, KeeperException {
        assertThat(zooKeeper.exists(testLockPath, false), is(nullValue()));
        executor.withLock("Test Lock", testLockPath, new DistributedOperation() {
            @Override
            public Object execute() throws DistributedOperationException {
                assertNumberOfChildren(zooKeeper, testLockPath, 1);
                return null;
            }
        });
        assertNumberOfChildren(zooKeeper, testLockPath, 0);
    }

    @Test
    public void testWithLockHavingSpecifiedTimeout() throws InterruptedException, KeeperException {
        assertThat(zooKeeper.exists(testLockPath, false), is(nullValue()));
        final Object opResult = "success";
        DistributedOperationResult result = executor.withLock("Test Lock w/Timeout", testLockPath,
                new DistributedOperation() {
                    @Override
                    public Object execute() throws DistributedOperationException {
                        return opResult;
                    }
                }, 10, TimeUnit.SECONDS);
        assertThat(result.timedOut, is(false));
        assertThat(result.result, is(opResult));
    }

    @Test
    public void testWithLockHavingACLAndHavingSpecifiedTimeout() throws InterruptedException, KeeperException {
        assertThat(zooKeeper.exists(testLockPath, false), is(nullValue()));
        final Object opResult = "success";
        DistributedOperationResult result = executor.withLock("Test Lock w/Timeout", testLockPath, ZooDefs.Ids.OPEN_ACL_UNSAFE,
                new DistributedOperation() {
                    @Override
                    public Object execute() throws DistributedOperationException {
                        return opResult;
                    }
                }, 10, TimeUnit.SECONDS);
        assertThat(result.timedOut, is(false));
        assertThat(result.result, is(opResult));
    }

    @Test
    public void testWithLockForMultipleLocksInDifferentThreads() throws InterruptedException, KeeperException {
        assertThat(zooKeeper.exists(testLockPath, false), is(nullValue()));
        List<TestDistOp> ops = Arrays.asList(
                new TestDistOp("op-1"),
                new TestDistOp("op-2"),
                new TestDistOp("op-3"),
                new TestDistOp("op-4")
        );

        List<Thread> opThreads = new ArrayList<Thread>();
        for (TestDistOp op : ops) {
            opThreads.add(launchDistributedOperation(op));
            Thread.sleep(10);
        }

        for (Thread opThread : opThreads) {
            opThread.join();
        }

        assertThat(TestDistOp.callCount.get(), is(ops.size()));
        for (TestDistOp op : ops) {
            assertThat(op.executed.get(), is(true));
        }
    }

    private Thread launchDistributedOperation(final TestDistOp op) {
        Thread opThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    executor.withLock(op.name, testLockPath, op);
                } catch (Exception ex) {
                    throw new DistributedOperationException(ex);
                }
            }
        });
        opThread.start();
        return opThread;
    }

    static class TestDistOp implements DistributedOperation {

        static AtomicInteger callCount = new AtomicInteger(0);

        final String name;
        final AtomicBoolean executed;

        TestDistOp(String name) {
            this.name = name;
            this.executed = new AtomicBoolean(false);
        }

        @Override
        public Object execute() throws DistributedOperationException {
            callCount.incrementAndGet();
            executed.set(true);
            return null;
        }
    }

    private void assertNumberOfChildren(ZooKeeper zk, String path, int expectedNumber) {
        List<String> children;
        try {
            children = zk.getChildren(path, false);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        assertThat(children.size(), is(expectedNumber));
    }
}
