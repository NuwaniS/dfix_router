package com.mubasher.oms.dfixrouter.server.fix.flowcontrol;

import com.mubasher.oms.dfixrouter.constants.IConstants;
import com.mubasher.oms.dfixrouter.logs.DFIXLogHandlerI;
import com.mubasher.oms.dfixrouter.logs.Log4j2Handler;
import quickfix.FieldNotFound;
import quickfix.Message;
import quickfix.field.*;
import quickfix.fix42.NewOrderSingle;
import quickfix.fix42.OrderCancelReplaceRequest;

import java.util.Arrays;
import java.util.HashMap;

public class FlowController implements Runnable {
    private static final DFIXLogHandlerI logger = new Log4j2Handler("com.mubasher.oms.dfixrouter.server.fix.flowcontrol.FlowController");
    private final int[] addedNewMessages;
    private final int[] addedAmendMessages;
    private final int windowSize;
    private final HashMap<String, Integer> duplicateMessageMap;
    private int currentCounter = 0;
    private int newMessageRate = Integer.MAX_VALUE;
    private int amendMessageRate = Integer.MAX_VALUE;
    private int totNewMessages;
    private int totAmendMessages;
    private int newMessageWindowLimit = Integer.MAX_VALUE;
    private int amendMessageWindowLimit = Integer.MAX_VALUE;
    private int duplicateMessageWindowLimit = Integer.MAX_VALUE;
    private boolean isReachedWindowSize;
    private boolean isSustainedBlockedNewOrder;
    private boolean isSustainedBlockedAmendOrder;
    private boolean isAlive = false;
    private boolean blockDuplicateByCliOrdId;

    public FlowController(int windowSize) {
        this.windowSize = windowSize;
        this.addedNewMessages = new int[this.windowSize];
        this.addedAmendMessages = new int[this.windowSize];
        duplicateMessageMap = new HashMap<>();
        initiate();
    }

    public void initiate() {
        currentCounter = 0;
        Arrays.fill(addedNewMessages, 0);
        Arrays.fill(addedAmendMessages, 0);
        totNewMessages = 0;
        totAmendMessages = 0;
        isReachedWindowSize = false;
        duplicateMessageMap.clear();
        isSustainedBlockedNewOrder = false;
        isSustainedBlockedAmendOrder = false;
    }

    public void setNewMessageRate(int newMessageRate) {
        this.newMessageRate = newMessageRate;
    }

    public void setAmendMessageRate(int amendMessageRate) {
        this.amendMessageRate = amendMessageRate;
    }

    public void setNewMessageWindowLimit(int newMessageWindowLimit) {
        this.newMessageWindowLimit = newMessageWindowLimit;
    }

    public void setAmendMessageWindowLimit(int amendMessageWindowLimit) {
        this.amendMessageWindowLimit = amendMessageWindowLimit;
    }

    public void setDuplicateMessageWindowLimit(int duplicateMessageWindowLimit) {
        this.duplicateMessageWindowLimit = duplicateMessageWindowLimit;
    }

    public void setBlockDuplicateByCliOrdId(boolean blockDuplicateByCliOrdId) {
        this.blockDuplicateByCliOrdId = blockDuplicateByCliOrdId;
    }

    @Override
    public void run() {
        isAlive = true;
        if (currentCounter == windowSize) {
            logger.info("Flow Controller: Window Size is reached.");
            currentCounter = 0;
            isReachedWindowSize = true;
            duplicateMessageMap.clear();
        }
        if (isReachedWindowSize) {
            totNewMessages -= addedNewMessages[currentCounter];
            totAmendMessages -= addedAmendMessages[currentCounter];
            addedNewMessages[currentCounter] = 0;
            addedAmendMessages[currentCounter] = 0;
        }
        currentCounter++;

    }

    public FlowControlStatus isSendMessage(Message message, boolean isFromFix) {
        FlowControlStatus flowControlStatus = FlowControlStatus.ALLOWED;
        try {
            if (duplicateMessageWindowLimit > 0) {
                flowControlStatus = checkDuplicateMessage(message, isFromFix);
            }
            if (message.getHeader().getString(MsgType.FIELD).equals(NewOrderSingle.MSGTYPE)
                    && newMessageRate > 0
                    && newMessageWindowLimit > 0
                    && flowControlStatus == FlowControlStatus.ALLOWED) {
                if (isSustainedBlockedNewOrder) {
                    flowControlStatus = FlowControlStatus.NEW_ORDER_SUSTAINED_BLOCKED;
                } else {
                    flowControlStatus = checkNewOrder();
                }
            } else if (message.getHeader().getString(MsgType.FIELD).equals(OrderCancelReplaceRequest.MSGTYPE)
                    && amendMessageRate > 0
                    && amendMessageWindowLimit > 0
                    && flowControlStatus == FlowControlStatus.ALLOWED) {
                if (isSustainedBlockedAmendOrder) {
                    flowControlStatus = FlowControlStatus.AMEND_ORDER_SUSTAINED_BLOCKED;
                } else {
                    flowControlStatus = checkAmendOrder();
                }
            }
        } catch (FieldNotFound fieldNotFound) {
            logger.error("Error at FlowController send validation: " + fieldNotFound.getMessage(), fieldNotFound);
        }
        return flowControlStatus;
    }

    private FlowControlStatus checkDuplicateMessage(Message message, boolean isFromFix) {
        return blockDuplicateByCliOrdId ? checkDuplicateMessageByCliOrdId(message, isFromFix) : checkDuplicateMessageDefault(message);
    }

    //check for duplicate messages using CliOrdId (tag 11 )
    private FlowControlStatus checkDuplicateMessageByCliOrdId(Message message, boolean isFromFix) {
        FlowControlStatus flowControlStatus = FlowControlStatus.ALLOWED;
        if (!isFromFix){//only validate for duplicate by CliOrdId for fix messages sent to exchange
            try {
                String key = message.getHeader().getString(MsgType.FIELD) +
                        message.getString(ClOrdID.FIELD);
                int duplicateMessageCount = (duplicateMessageMap.containsKey(key) ? duplicateMessageMap.get(key) + 1 : 0);
                duplicateMessageMap.put(key, duplicateMessageCount);
                logger.debug("Duplicate messages Key: " + key + " | Count: " + duplicateMessageCount);
                if (duplicateMessageCount > IConstants.CONSTANT_ZERO_0) {
                    flowControlStatus = FlowControlStatus.CLIORDID_DUPLICATE_BLOCKED;
                    logger.debug("Duplicate message found the key: " + key + " - Order Blocked");
                }
            } catch (FieldNotFound fieldNotFound) {
                logger.error("Error at Duplicate Validation by ClOrdId: " + fieldNotFound.getMessage(), fieldNotFound);
            }
        }
        return flowControlStatus;
    }

    private FlowControlStatus checkDuplicateMessageDefault(Message message) {
        FlowControlStatus flowControlStatus = FlowControlStatus.ALLOWED;
        try {
            StringBuilder sb = new StringBuilder();
            sb.append(message.getHeader().getString(MsgType.FIELD));
            sb.append(message.getString(Symbol.FIELD));
            sb.append(message.getChar(Side.FIELD));
            if (message.isSetField(Price.FIELD)) {
                sb.append(message.getDouble(Price.FIELD));
            }
            sb.append(message.getDouble(OrderQty.FIELD));
            String key = sb.toString();
            int duplicateMessage = (duplicateMessageMap.containsKey(key) ? duplicateMessageMap.get(key).intValue() : 0);
            duplicateMessage++;
            duplicateMessageMap.put(key, duplicateMessage);
            logger.debug("Duplicate messages Key: " + key + " | Count: " + duplicateMessage);
            if (duplicateMessage > duplicateMessageWindowLimit) {
                flowControlStatus = FlowControlStatus.DUPLICATE_SUSTAINED_BLOCKED;
                logger.debug("Duplicate limit reached for the key: " + key + " - Sustained block created.");
            }
        } catch (FieldNotFound fieldNotFound) {
            logger.error("Error at duplicate validation: " + fieldNotFound.getMessage(), fieldNotFound);
        }
        return flowControlStatus;
    }

    private FlowControlStatus checkNewOrder() {
        FlowControlStatus flowControlStatus = FlowControlStatus.ALLOWED;
        int counter = getCounter();
        addedNewMessages[counter]++;
        totNewMessages++;
        logger.debug("Total New Orders: " + totNewMessages + " New Orders for Current Counter: " + addedNewMessages[counter]);
        isSustainedBlockedNewOrder = totNewMessages >= newMessageWindowLimit;
        boolean isSendMessage = !isSustainedBlockedNewOrder;
        if (isSendMessage) {
            isSendMessage = newMessageRate >= addedNewMessages[counter];
        }
        if (isSustainedBlockedNewOrder) {
            flowControlStatus = FlowControlStatus.NEW_ORDER_SUSTAINED_BLOCKED;
            logger.debug("Sustained block created for New Order Window Limit violation at counter: " + counter);
        } else if (!isSendMessage) {
            flowControlStatus = FlowControlStatus.TEMPORARY_BLOCKED;
            logger.debug("New Order limit violated at counter: " + counter);
        }
        return flowControlStatus;
    }

    private FlowControlStatus checkAmendOrder() {
        FlowControlStatus flowControlStatus = FlowControlStatus.ALLOWED;
        int counter = getCounter();
        addedAmendMessages[counter]++;
        totAmendMessages++;
        logger.debug("Total Amend Orders: " + totAmendMessages + " Amend Orders for Current Counter: " + addedAmendMessages[counter]);
        isSustainedBlockedAmendOrder = totAmendMessages >= amendMessageWindowLimit;
        boolean isSendMessage = !isSustainedBlockedAmendOrder;
        if (isSendMessage) {
            isSendMessage = amendMessageRate >= addedAmendMessages[counter];
        }
        if (isSustainedBlockedAmendOrder) {
            flowControlStatus = FlowControlStatus.AMEND_ORDER_SUSTAINED_BLOCKED;
            logger.debug("Sustained block created for Amend Order Window Limit violation at counter: " + counter);
        } else if (!isSendMessage) {
            flowControlStatus = FlowControlStatus.TEMPORARY_BLOCKED;
            logger.debug("Amend Order limit violated at counter: " + counter);
        }
        return flowControlStatus;
    }

    public int getCounter() {
        int counter = currentCounter;
        if (counter == windowSize) {
            counter--;
        }
        return counter;
    }

    public synchronized boolean isAlive() {
        return isAlive;
    }
}
