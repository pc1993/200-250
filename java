package com.example.gmasystem.Thread;

import android.os.Handler;
import android.util.Log;

import com.example.gmasystem.StaticInfomation.SystemStateSettings;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ReorderingManager {
    private static final String TAG = ReorderingManager.class.getSimpleName();
    private int reorderTimerFinish = 1;
    private int reorderTimerSet = 0;
    private int tFlag = 0;
    private int startReorderingDelayTime = 50;

    private final int lteVectorLimitSize = 1000;
    private Vector<datastorge> lteDataVector;
    private int lteVectorBegin = 0;
    private int lteVectorEnd = 0;

    private final int wifiVectorLimitSize = 1000;
    private Vector<datastorge> wifiDataVector;
    private int wifiVectorBegin = 0;
    private int wifiVectorEnd = 0;

    private int beginEnd = 0;

    private int nextSn = 0;
    private int lastSn = 0;
    private FileOutputStream fileOutputStream;

    private Handler reorderingHandler;
    private ReorderingTimer reorderingTimer;
    private ScheduledExecutorService scheduledThreadPool;// = Executors.newScheduledThreadPool(3);
    private ExecutorService executorService;

    public ReorderingManager(ExecutorService executorService) {
        lteDataVector = new Vector<datastorge>(lteVectorLimitSize);
        wifiDataVector = new Vector<datastorge>(wifiVectorLimitSize);
        reorderingHandler = new Handler();
        reorderingTimer = new ReorderingTimer();
        this.executorService = executorService;
    }

    public void addPacketToWifiVector(byte[] packet, int seqNum, int len) {
        //Log.v("vector size",":" + wifiDataVector.size() + ":" + wifiVectorEnd);
        if (wifiDataVector.size() < 1000) {
            wifiDataVector.add(wifiVectorEnd, new datastorge(len, seqNum, packet));
        } else {
            wifiDataVector.get(wifiVectorEnd).data = packet;
            wifiDataVector.get(wifiVectorEnd).len = len;
            wifiDataVector.get(wifiVectorEnd).sn = seqNum;
        }
        wifiVectorEnd = (wifiVectorEnd + 1) % wifiVectorLimitSize;
        checkIsStartReorderingTimer();
    }

    public void addPacketToLteVector(byte[] packet, int seqNum, int len) {
        if (lteDataVector.size() < 1000) {
            lteDataVector.add(lteVectorEnd, new datastorge(len, seqNum, packet));
        } else {
            lteDataVector.get(lteVectorEnd).data = packet;
            lteDataVector.get(lteVectorEnd).len = len;
            lteDataVector.get(lteVectorEnd).sn = seqNum;
        }
        lteVectorEnd = (lteVectorEnd + 1) % lteVectorLimitSize;
        checkIsStartReorderingTimer();
    }

    private void checkIsStartReorderingTimer() {
        if (reorderTimerFinish == 1) {
            if (reorderTimerSet == 0 || ((tFlag > 0) && (wifiVectorEnd != wifiVectorBegin) && (lteVectorEnd != lteVectorBegin))) {
                beginReordering(1);
            }
        }
    }

    public class datastorge {
        private int len;
        private int sn;
        private byte[] data;

        public datastorge(int len, int sn, byte[] data) {
            this.len = len;
            this.sn = sn;
            this.data = data;
        }
    }

    private void beginReordering(int flag) {
        reorderTimerSet = 1;
        tFlag = flag;
        beginEnd = wifiVectorEnd;
        reorderingHandler.postDelayed(reorderingTimer, flag == 0 ? 0 : startReorderingDelayTime);
    }

    public class ReorderingTimer implements Runnable {
        private int gmaHeaderLen = 11;

        @Override
        public void run() {
            reorderTimerSet = 0;
            if (reorderTimerFinish == 1) {
                reorderTimerFinish = 0;
                int wifiVectorSize = (wifiVectorEnd - wifiVectorBegin + wifiVectorLimitSize) % wifiVectorLimitSize;
                int lteVectorSize = (lteVectorEnd - lteVectorBegin + lteVectorLimitSize) % lteVectorLimitSize;
                if(tFlag == 1 && (wifiVectorSize * lteVectorSize == 0)){
                    clearWifiVector(wifiVectorSize);
                    clearLteVector(lteVectorSize);
                }
                else{
                    while(wifiVectorEnd != wifiVectorBegin && lteVectorBegin != lteVectorEnd){
                        if(rollOverDiff(wifiDataVector.get(wifiVectorBegin).sn,lteDataVector.get(lteVectorBegin).sn) <= 0 ){
                            try {
                                fileOutputStream.write(wifiDataVector.get(wifiVectorBegin).data,gmaHeaderLen,wifiDataVector.get(wifiVectorBegin).len);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            if (rollOverDiff(wifiDataVector.get(wifiVectorBegin).sn, nextSn) == 0) {
                                nextSn = (wifiDataVector.get(wifiVectorBegin).sn + 1) & 0x00FFFFFF;
                            }
                            wifiVectorBegin = (wifiVectorBegin + 1) % wifiVectorLimitSize;
                        }
                        else{
                            try {
                                fileOutputStream.write(lteDataVector.get(lteVectorBegin).data,gmaHeaderLen,lteDataVector.get(lteVectorBegin).len);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            if (rollOverDiff(lteDataVector.get(lteVectorBegin).sn, nextSn) >= 0) {
                                nextSn = (lteDataVector.get(lteVectorBegin).sn + 1) & 0x00FFFFFF;
                            }
                            lteVectorBegin = (lteVectorBegin + 1) % lteVectorLimitSize;
                        }
                    }

                    while (wifiVectorEnd != wifiVectorBegin && rollOverDiff(wifiDataVector.get(wifiVectorBegin).sn, nextSn) <= 0) {
                        try {
                            fileOutputStream.write(wifiDataVector.get(wifiVectorBegin).data,gmaHeaderLen,wifiDataVector.get(wifiVectorBegin).len);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        if (rollOverDiff(wifiDataVector.get(wifiVectorBegin).sn, nextSn) >= 0) {
                            nextSn = (wifiDataVector.get(wifiVectorBegin).sn + 1) & 0x00FFFFFF;
                        }
                        wifiVectorBegin = (wifiVectorBegin + 1) % wifiVectorLimitSize;
                    }

                    while (lteVectorEnd != lteVectorBegin && rollOverDiff(lteDataVector.get(lteVectorBegin).sn, nextSn) <= 0) {
                        try {
                            fileOutputStream.write(lteDataVector.get(lteVectorBegin).data,gmaHeaderLen,lteDataVector.get(lteVectorBegin).len);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        if (rollOverDiff(lteDataVector.get(lteVectorBegin).sn, nextSn) == 0) {
                            nextSn = (lteDataVector.get(lteVectorBegin).sn + 1) & 0x00FFFFFF;
                        }
                        lteVectorBegin = (lteVectorBegin + 1) % lteVectorLimitSize;
                    }
                }
                if (SystemStateSettings.splitEnable == 1) {
                    if (wifiVectorEnd != wifiVectorBegin && lteVectorEnd != lteVectorBegin) {
                        beginReordering(0);
                    } else if ((wifiVectorEnd - wifiVectorBegin + lteVectorEnd - lteVectorBegin) != 0) {
                        beginReordering(1);
                    } else {
                        reorderTimerSet = 0;
                    }
                }
                reorderTimerFinish = 1;
            }
        }

        private void clearWifiVector(int wifiVectorSize) {
            while(wifiVectorSize > 0 && lteVectorEnd == lteVectorBegin){
                try {
                    fileOutputStream.write(wifiDataVector.get(wifiVectorBegin).data, gmaHeaderLen,wifiDataVector.get(wifiVectorBegin).len);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                if(rollOverDiff(nextSn,wifiDataVector.get(wifiVectorBegin).sn) <= 0){
                    nextSn = (wifiDataVector.get(wifiVectorBegin).sn + 1) & 0X00FFFFFF;
                }
                wifiVectorBegin = (wifiVectorBegin + 1) % wifiVectorLimitSize;
                wifiVectorSize--;
            }
        }

        private void clearLteVector(int lteVectorSize) {
            while (lteVectorSize > 0 && wifiVectorEnd == wifiVectorBegin) {
                try {
                    fileOutputStream.write(lteDataVector.get(lteVectorBegin).data, gmaHeaderLen, lteDataVector.get(lteVectorBegin).len);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                if (rollOverDiff(nextSn, lteDataVector.get(lteVectorBegin).sn) <= 0) {
                    nextSn = (lteDataVector.get(lteVectorBegin).sn + 1) & 0x00FFFFFF;
                }
                lteVectorBegin = (lteVectorBegin + 1) % lteVectorLimitSize;
                lteVectorSize--;
            }
        }

        private int rollOverDiff(int x, int y) {
            int diff = x - y;
            if (diff > (0x00FFFFFF >> 1)) {
                diff = diff - 0x00FFFFFF - 1;
            } else if (diff < -(0x00FFFFFF >> 1)) {
                diff = diff + 0x00FFFFFF + 1;
            }
            return diff;
        }

        private int getGSeqNum(byte[] mData, int mOffset) {
            return (int) ((Byte.toUnsignedInt(mData[mOffset + 8]) << 16) + (Byte.toUnsignedInt(mData[mOffset + 7]) << 8) + Byte.toUnsignedInt(mData[mOffset + 6]));
        }
    }

    public void updateNextSn(int receiveDataSn) {
        lastSn = receiveDataSn;
        nextSn = (receiveDataSn + 1) & 0x00FFFFFF;
    }

    public int getNextSn() {
        return nextSn;
    }

    public int getLteVectorBegin() {
        return lteVectorBegin;
    }

    public int getLteVectorEnd() {
        return lteVectorEnd;
    }

    public int getLteVectorLimitSize() {
        return lteVectorLimitSize;
    }

    public int getWifiVectorBegin() {
        return wifiVectorBegin;
    }

    public int getWifiVectorEnd() {
        return wifiVectorEnd;
    }

    public int getWifiVectorLimitSize() {
        return wifiVectorLimitSize;
    }

    public void updateOutputStream(FileOutputStream fileOutputStream) {
        this.fileOutputStream = fileOutputStream;
    }
}
