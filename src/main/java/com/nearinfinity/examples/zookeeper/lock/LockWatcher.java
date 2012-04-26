package com.nearinfinity.examples.zookeeper.lock;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Semaphore;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;

import com.nearinfinity.examples.zookeeper.util.ConnectionHelper;

public class LockWatcher implements Watcher {

    private ZooKeeper zk;
    private String lockPath;
    private Semaphore semaphore = new Semaphore(1);

    public static void main(String[] args) throws IOException, InterruptedException, KeeperException {
        String hosts = args[0];
        String lockPath = args[1];

        ConnectionHelper connectionHelper = new ConnectionHelper();
        ZooKeeper zk = connectionHelper.connect(hosts);

        LockWatcher watcher = new LockWatcher(zk, lockPath);
        watcher.watch();
    }

    public LockWatcher(ZooKeeper zk, String lockPath) throws InterruptedException, KeeperException {
        this.zk = zk;
        this.lockPath = lockPath;
        ensureLockPathExists(zk, lockPath);
    }

    public void watch() throws InterruptedException, KeeperException {
        System.out.println("Acquire initial semaphore");
        semaphore.acquire();

        // noinspection InfiniteLoopStatement
        while (true) {
            System.out.println("Getting children");
            List<String> children = zk.getChildren(lockPath, this);
            printChildren(children);
            semaphore.acquire();
        }
    }

    @Override
    public void process(WatchedEvent event) {
        if (event.getType() == Event.EventType.NodeChildrenChanged) {
            System.out.printf("Received %s event\n", Event.EventType.NodeChildrenChanged.name());
            semaphore.release();
        }
    }

    private void ensureLockPathExists(ZooKeeper zk, String lockPath)
            throws InterruptedException, KeeperException {

        if (zk.exists(lockPath, false) == null) {
            String znodePath =
                    zk.create(lockPath, null /* data */, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            System.out.printf("Created lock znode having path %s\n", znodePath);
        }
    }

    private void printChildren(List<String> children) {
        if (children.isEmpty()) {
            System.out.println("No one has the lock at the moment...");
            return;
        }

        System.out.println("Current lock nodes:");
        for (String child : children) {
            System.out.printf("  %s\n", child);
        }
        System.out.println("--------------------");
    }
}
