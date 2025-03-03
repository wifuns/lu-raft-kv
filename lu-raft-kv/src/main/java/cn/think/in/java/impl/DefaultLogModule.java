package cn.think.in.java.impl;

import java.io.File;
import java.util.concurrent.locks.ReentrantLock;

import com.alibaba.fastjson.JSON;

import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.think.in.java.LogModule;
import cn.think.in.java.entity.LogEntry;
import cn.think.in.java.util.PortUtil;
import cn.think.in.java.util.WorkId;
import lombok.Getter;
import lombok.Setter;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 *
 * 默认的日志实现. 日志模块不关心 key, 只关心 index.
 *
 * @author 莫那·鲁道
 * @see cn.think.in.java.entity.LogEntry
 */
@Setter
@Getter
public class DefaultLogModule implements LogModule {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultLogModule.class);


    /** public just for test */
    public  String dbDir;
    public  String logsDir;

    private  RocksDB logDb;

    public final static byte[] LAST_INDEX_KEY = "LAST_INDEX_KEY".getBytes();

    ReentrantLock lock = new ReentrantLock();

    static { 
        RocksDB.loadLibrary();
    }

    private DefaultLogModule() {
    	if (dbDir == null) {
             dbDir = "./rocksDB-raft/" + PortUtil.currentPort();
        }
        if (logsDir == null) {
             logsDir = dbDir + "/logModule";
        }
        Options options = new Options();
        options.setCreateIfMissing(true);

        File file = new File(logsDir);
        boolean success = false;
        if (!file.exists()) {
            success = file.mkdirs();
        }
        if (success) {
            LOGGER.warn(" make a new dir : " + logsDir);
        }
        try {
            logDb = RocksDB.open(options, logsDir);
        } catch (RocksDBException e) {
            e.printStackTrace();
        }
    }

    private static final ThreadLocal<DefaultLogModule> NODE_INFO = new ThreadLocal<DefaultLogModule>();
    public static DefaultLogModule getInstance() {
    	//改成每个线程一个实例 
    	if(NODE_INFO.get() == null){
    		DefaultLogModule nodeInfo = new DefaultLogModule();
    		NODE_INFO.set(nodeInfo);
    		return nodeInfo;
    	}else{
    		return NODE_INFO.get();
    	}
    	//return DefaultLogsLazyHolder.INSTANCE;
    }  

    private static class DefaultLogsLazyHolder {

        private static final DefaultLogModule INSTANCE = new DefaultLogModule();
    }

    /**
     * logEntry 的 index 就是 key. 严格保证递增.
     *
     * @param logEntry
     */
    @Override
    public void write(LogEntry logEntry) {

        boolean success = false;
        try {
            lock.tryLock(3000, MILLISECONDS);
            //新增条目所以index+1
            logEntry.setIndex(getLastIndex() + 1);
            logDb.put(logEntry.getIndex().toString().getBytes(), JSON.toJSONBytes(logEntry));
            success = true;
            LOGGER.info(" DefaultLogModule write rocksDB success, logEntry info : [{}]", logEntry);
        } catch (RocksDBException | InterruptedException e) {
            LOGGER.warn(e.getMessage());
        } finally {
            if (success) {
                updateLastIndex(logEntry.getIndex());
            }
            lock.unlock();
        }
    }


    @Override
    public LogEntry read(Long index) {
        try {
            byte[] result = logDb.get(convert(index));
            if (result == null) {
                return null;
            }
            return JSON.parseObject(result, LogEntry.class);
        } catch (RocksDBException e) {
            LOGGER.warn(e.getMessage(), e);
        }
        return null;
    }

    @Override
    public void removeOnStartIndex(Long startIndex) {
        boolean success = false;
        int count = 0;
        try {
            lock.tryLock(3000, MILLISECONDS);
            for (long i = startIndex; i <= getLastIndex(); i++) {
                logDb.delete(String.valueOf(i).getBytes());
                ++count;
            }
            success = true;
            LOGGER.warn(" rocksDB removeOnStartIndex success, count={} startIndex={}, lastIndex={}", count, startIndex, getLastIndex());
        } catch (InterruptedException | RocksDBException e) {
            LOGGER.warn(e.getMessage());
        } finally {
            if (success) {
                updateLastIndex(getLastIndex() - count);
            }
            lock.unlock();
        }
    }


    @Override
    public LogEntry getLast() {
        try {
            byte[] result = logDb.get(convert(getLastIndex()));
            if (result == null) {
                return null;
            }
            return JSON.parseObject(result, LogEntry.class);
        } catch (RocksDBException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public Long getLastIndex() {
        byte[] lastIndex = "-1".getBytes();
        try {
            lastIndex = logDb.get(LAST_INDEX_KEY);
            if (lastIndex == null) {
                lastIndex = "-1".getBytes();
            }
        } catch (RocksDBException e) {
            e.printStackTrace();
        }
        return Long.valueOf(new String(lastIndex));
    }

    private byte[] convert(Long key) {
        return key.toString().getBytes();
    }

    // on lock
    private void updateLastIndex(Long index) {
        try {
            // overWrite
            logDb.put(LAST_INDEX_KEY, index.toString().getBytes());
        } catch (RocksDBException e) {
            e.printStackTrace();
        }
    }


}
