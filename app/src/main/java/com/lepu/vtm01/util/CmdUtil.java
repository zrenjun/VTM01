package com.lepu.vtm01.util;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

public class CmdUtil {

    private static final int HEAD = 0xA5;
    private static final int TYPE_NORMAL_SEND = 0x00;
    public static final int TYPE_NORMAL_RESPONSE = 0x01;
    public static final int TYPE_FILE_NOT_FOUND = 0xE0;
    public static final int TYPE_FILE_READ_FAILED = 0xE1;
    public static final int TYPE_FILE_WRITE_FAILED = 0xE2;
    public static final int TYPE_FIRMWARE_UPDATE_FAILED = 0xE3;
    public static final int TYPE_LANGUAGE_UPDATE_FAILED = 0xE4;
    public static final int TYPE_PARAM_ILLEGAL = 0xF1;
    public static final int TYPE_PERMISSION_DENIED = 0xF2;
    public static final int TYPE_DECRYPT_FAILED = 0xF3;
    public static final int TYPE_DEVICE_BUSY = 0xFB;
    public static final int TYPE_CMD_FORMAT_ERROR = 0xFC;
    public static final int TYPE_CMD_NOT_SUPPORTED = 0xFD;
    public static final int TYPE_NORMAL_ERROR = 0xFF;

    public static final int ECHO = 0xE0;
    public static final int GET_INFO = 0xE1;
    public static final int GET_CONFIG = 0x00;
    public static final int SET_CONFIG = 0x06;
    public static final int RT_DATA = 0x02;

    public static int seqNo = 0;
    private static void addNo() {
        seqNo++;
        if (seqNo >= 255) {
            seqNo = 0;
        }
    }

    public static byte[] echo(byte[] data) {
        return getReq(ECHO, data);
    }

    public static byte[] getRtData() {
        return getReq(RT_DATA, new byte[0]);
    }
    public static byte[] getInfo() {
        return getReq(GET_INFO, new byte[0]);
    }
    public static byte[] getConfig() {
        return getReq(GET_CONFIG, new byte[0]);
    }
    public static byte[] setConfig(byte[] data) {
        return getReq(SET_CONFIG, data);
    }

    public static byte[] getReq(int sendCmd, byte[] data) {
        int len = data.length;
        byte[] cmd = new byte[7+len];

        cmd[0] = (byte) HEAD;
        cmd[1] = (byte) sendCmd;
        cmd[2] = (byte) ~sendCmd;
        cmd[3] = (byte) TYPE_NORMAL_SEND;
        cmd[4] = (byte) seqNo;
        // length
        cmd[5] = (byte) len;
        cmd[6] = (byte) (len>>8);
        System.arraycopy(data, 0, cmd, 7, len);
        addNo();
        return cmd;
    }

    public static String trimStr(String s) {
        return s.trim();
    }

    public static String stringFromDate(Date date, String formatString) {
        DateFormat df = new SimpleDateFormat(formatString);
        return df.format(date);
    }

    public static int getTimeZoneOffset() {
        return TimeZone.getDefault().getOffset(System.currentTimeMillis());
    }

}
