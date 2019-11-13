package cn.think.in.java.common;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import cn.think.in.java.impl.DefaultLogModule;

/**
 *
 * 节点集合. 去重.
 *
 * @author 莫那·鲁道
 */
public class PeerSet implements Serializable {

    private List<Peer> list = new ArrayList<>();

    private volatile Peer leader;

    /** final */
    private volatile Peer self;

    private PeerSet() {
    }

    private static final ThreadLocal<PeerSet> NODE_INFO = new ThreadLocal<PeerSet>();
    public static PeerSet getInstance() {
    	//改成每个线程一个实例 
    	if(NODE_INFO.get() == null){
    		PeerSet nodeInfo = new PeerSet();
    		NODE_INFO.set(nodeInfo);
    		return nodeInfo;
    	}else{
    		return NODE_INFO.get();
    	}
    	//PeerSetLazyHolder.INSTANCE;
    }  
     
    private static class PeerSetLazyHolder {

        private static final PeerSet INSTANCE = new PeerSet();
    }

    public void setSelf(Peer peer) {
        self = peer;
    }

    public Peer getSelf() {
        return self;
    }

    public void addPeer(Peer peer) {
        list.add(peer);
    }

    public void removePeer(Peer peer) {
        list.remove(peer);
    }

    public List<Peer> getPeers() {
        return list;
    }

    public List<Peer> getPeersWithOutSelf() {
        List<Peer> list2 = new ArrayList<>(list);
        list2.remove(self);
        return list2;
    }


    public Peer getLeader() {
        return leader;
    }

    public void setLeader(Peer peer) {
        leader = peer;
    }

    @Override
    public String toString() {
        return "PeerSet{" +
            "list=" + list +
            ", leader=" + leader +
            ", self=" + self +
            '}';
    }
}
