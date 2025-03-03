package cn.think.in.java.impl;

import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.think.in.java.Consensus;
import cn.think.in.java.common.NodeStatus;
import cn.think.in.java.common.Peer;
import cn.think.in.java.entity.AentryParam;
import cn.think.in.java.entity.AentryResult;
import cn.think.in.java.entity.LogEntry;
import cn.think.in.java.entity.RvoteParam;
import cn.think.in.java.entity.RvoteResult;
import io.netty.util.internal.StringUtil;
import lombok.Getter;
import lombok.Setter;

/**
 *
 * 默认的一致性模块实现.
 *
 * @author 莫那·鲁道
 */
@Setter
@Getter
public class DefaultConsensus implements Consensus {


    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultConsensus.class);


    public final DefaultNode node;

    public final ReentrantLock voteLock = new ReentrantLock();
    public final ReentrantLock appendLock = new ReentrantLock();

    public DefaultConsensus(DefaultNode node) { 
        this.node = node;
    }

    /**
     * 请求投票 RPC
     *
     * 接收者实现：
     *      如果term < currentTerm返回 false （5.2 节）
     *      如果 votedFor 为空或者就是 candidateId，并且候选人的日志至少和自己一样新，那么就投票给他（5.2 节，5.4 节）
     */
    @Override
    public RvoteResult requestVote(RvoteParam param) {
        try {
            LOGGER.info("开始选举");
            RvoteResult.Builder builder = RvoteResult.newBuilder();
            //有其他人已经发起了选举
            if (!voteLock.tryLock()) {
                return builder.term(node.getCurrentTerm()).voteGranted(false).build();
            }

            // 对方任期没有自己新
            if (param.getTerm() < node.getCurrentTerm()) {
                return builder.term(node.getCurrentTerm()).voteGranted(false).build();
            }

            // (当前节点并没有投票 或者 已经投票过了且是对方节点) && 对方日志和自己一样新
            LOGGER.info(" node {} current vote for [{}], param candidateId : {}", node.peerSet.getSelf(), node.getVotedFor(), param.getCandidateId());
            LOGGER.info(" node {} current term {}, peer term : {}", node.peerSet.getSelf(), node.getCurrentTerm(), param.getTerm());

            if ((StringUtil.isNullOrEmpty(node.getVotedFor()) || node.getVotedFor().equals(param.getCandidateId()))) {

                if (node.getLogModule().getLast() != null) {
                    // 先比较term，term大的优先级大
                    if (node.getLogModule().getLast().getTerm() > param.getLastLogTerm()) {
                        return RvoteResult.fail();
                    }
                    // term >= 自己，再比较lastLogIndex
                    if (node.getLogModule().getLastIndex() > param.getLastLogIndex()) {
                        return RvoteResult.fail();
                    }
                }

                // 切换状态
                node.status = NodeStatus.FOLLOWER;
                // 更新
                node.peerSet.setLeader(new Peer(param.getCandidateId()));
                node.setCurrentTerm(param.getTerm());
                node.setVotedFor(param.serverId);
                // 返回成功
                return builder.term(node.currentTerm).voteGranted(true).build();
            }

            return builder.term(node.currentTerm).voteGranted(false).build();

        } finally {
            voteLock.unlock();
        }
    }


    /**
     *  1、无论成功失败首先设置返回值，也就是将自己的 term 返回给 leader。
	 *	2、判断对方的 term 是否大于自身，如果大于自身，变成 follower，防止异步的选举任务误操作。同时更新选举时间和心跳时间。
	 *	3、如果对方 term 小于自身，返回失败。不更新选举时间和心跳时间。以便触发选举。
     * 
     * 附加日志(多个日志,为了提高效率) RPC
     *
     * 接收者实现：
     *    如果 term < currentTerm 就返回 false （5.1 节）
     *    如果日志在 prevLogIndex 位置处的日志条目的任期号和 prevLogTerm 不匹配，则返回 false （5.3 节）
     *    如果已经存在的日志条目和新的产生冲突（索引值相同但是任期号不同），删除这一条和之后所有的 （5.3 节）
     *    附加任何在已有的日志中不存在的条目
     *    如果 leaderCommit > commitIndex，令 commitIndex 等于 leaderCommit 和 新日志条目索引值中较小的一个
     */
    @Override
    public AentryResult appendEntries(AentryParam param) {
        AentryResult result = AentryResult.fail();
        try { 
            if (!appendLock.tryLock()) {
            	result.setNotChangeIndex(true);
                return result;
            }

            result.setTerm(node.getCurrentTerm());
            // 不够格
            if (param.getTerm() < node.getCurrentTerm()) {
                return result;
            }
            //重置时间
            node.preHeartBeatTime = System.currentTimeMillis();
            node.preElectionTime = System.currentTimeMillis();
            node.peerSet.setLeader(new Peer(param.getLeaderId()));

            // 够格
            if (param.getTerm() >= node.getCurrentTerm()) {
                LOGGER.debug( " node {} become FOLLOWER, currentTerm : {}, param Term : {}, param serverId",
                    node.peerSet.getSelf(), node.currentTerm, param.getTerm(), param.getServerId());
                // 认怂
                node.status = NodeStatus.FOLLOWER;
            }
            // 使用对方的 term.
            node.setCurrentTerm(param.getTerm());

            //日志为null时发送的是 心跳
            if (param.getEntries() == null || param.getEntries().length == 0) {
                //LOGGER.info(" node {} append heartbeat success , he's term : {}, my term : {}",
                    //param.getLeaderId(), param.getTerm(), node.getCurrentTerm());
                return AentryResult.builder().term(node.getCurrentTerm()).success(true).build();
            }
           /* 再来看看日志接收者的实现步骤：

		           1、和心跳一样，要先检查对方 term，如果 term 都不对，那么就没什么好说的了。
		           2、 如果日志不匹配，那么返回 leader，告诉他，减小 nextIndex 重试。
		           3、如果本地存在的日志和 leader 的日志冲突了，以 leader 的为准，删除自身的。
		           4、最后，将日志应用到状态机，更新本地的 commitIndex，返回 leader 成功。*/

           
            // 真实日志
            // 第一次
            if (node.getLogModule().getLastIndex() != 0 && param.getPrevLogIndex() != 0) {
                LogEntry logEntry;
                if ((logEntry = node.getLogModule().read(param.getPrevLogIndex())) != null) {
                    // 如果日志在 prevLogIndex 位置处的日志条目的任期号和 prevLogTerm 不匹配，则返回 false
                    // 需要减小 nextIndex 重试.
                    if (logEntry.getTerm() != param.getPreLogTerm()) {
                        return result;
                    }
                } else {
                    // index 不对, 需要递减 nextIndex 重试.
                    return result;
                }

            }

            // 检测新增的数据是否存在 getPrevLogIndex 是同步完成后的共识日志index,所以新增的 就是 getPrevLogIndex + 1
            LogEntry existLog = node.getLogModule().read(((param.getPrevLogIndex() + 1))); 
            if (existLog != null && existLog.getTerm() != param.getEntries()[0].getTerm()) {
                // 删除这一条和之后所有的, 然后写入日志和状态机.
            	node.getLogModule().removeOnStartIndex(param.getPrevLogIndex() + 1); 
            } else if (existLog != null) {
                // 已经有日志了, 不能重复写入.
                result.setSuccess(true);
                return result;
            }

            // 写进日志并且应用到状态机 
            LOGGER.info("将数据写入本地数据库和状态机中DefaultNode:{}",node);
            for (LogEntry entry : param.getEntries()) { 
                node.getLogModule().write(entry);
                node.stateMachine.apply(entry);
                result.setSuccess(true);
            }

            //如果 leaderCommit > commitIndex，令 commitIndex 等于 leaderCommit 和 新日志条目索引值中较小的一个
            if (param.getLeaderCommit() > node.getCommitIndex()) {
                int commitIndex = (int) Math.min(param.getLeaderCommit(), node.getLogModule().getLastIndex());
                node.setCommitIndex(commitIndex);
                node.setLastApplied(commitIndex);
            }

            result.setTerm(node.getCurrentTerm());

            node.status = NodeStatus.FOLLOWER;
            // TODO, 是否应当在成功回复之后, 才正式提交? 防止 leader "等待回复"过程中 挂掉.
            return result;
        } finally {
            appendLock.unlock();
        }
    }


}
