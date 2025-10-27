package sirsim.utils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * シンプルなログ機能を提供するクラス
 * 環境変数LOG_LEVELでログレベルを制御可能
 */
public class Logger {
    
    // ログレベル定義
    public enum LogLevel {
        DEBUG(0), INFO(1), WARN(2), ERROR(3);
        
        private final int level;
        
        LogLevel(int level) {
            this.level = level;
        }
        
        public int getLevel() {
            return level;
        }
    }
    
    // 現在のログレベル（環境変数またはデフォルト値から設定）
    private static final LogLevel CURRENT_LOG_LEVEL = getLogLevelFromEnv();
    
    // 日時フォーマッター
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    
    // クラス名（ログ出力時に使用）
    private final String className;
    
    /**
     * コンストラクタ
     * @param clazz ログを出力するクラス
     */
    public Logger(Class<?> clazz) {
        this.className = clazz.getSimpleName();
    }
    
    /**
     * 環境変数からログレベルを取得
     * @return 設定されたログレベル（デフォルト: INFO）
     */
    private static LogLevel getLogLevelFromEnv() {
        String logLevelStr = System.getenv("LOG_LEVEL");
        if (logLevelStr != null) {
            try {
                return LogLevel.valueOf(logLevelStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                System.err.println("警告: 無効なログレベル '" + logLevelStr + "'. INFOレベルを使用します。");
            }
        }
        return LogLevel.INFO; // デフォルト
    }
    
    /**
     * ログ出力の基本メソッド
     * @param level ログレベル
     * @param message メッセージ
     */
    private void log(LogLevel level, String message) {
        if (level.getLevel() >= CURRENT_LOG_LEVEL.getLevel()) {
            String timestamp = LocalDateTime.now().format(DATE_TIME_FORMATTER);
            System.out.printf("[%s] [%s] [%s] %s%n", timestamp, level.name(), className, message);
        }
    }
    
    /**
     * DEBUGレベルでログ出力
     * @param message メッセージ
     */
    public void debug(String message) {
        log(LogLevel.DEBUG, message);
    }
    
    /**
     * INFOレベルでログ出力
     * @param message メッセージ
     */
    public void info(String message) {
        log(LogLevel.INFO, message);
    }
    
    /**
     * WARNレベルでログ出力
     * @param message メッセージ
     */
    public void warn(String message) {
        log(LogLevel.WARN, message);
    }
    
    /**
     * ERRORレベルでログ出力
     * @param message メッセージ
     */
    public void error(String message) {
        log(LogLevel.ERROR, message);
    }
    
    /**
     * 現在のログレベルを取得
     * @return 現在のログレベル
     */
    public static LogLevel getCurrentLogLevel() {
        return CURRENT_LOG_LEVEL;
    }
    
    /**
     * ログレベルが指定レベル以上かチェック
     * @param level チェックするログレベル
     * @return 指定レベル以上の場合true
     */
    public static boolean isLogLevelEnabled(LogLevel level) {
        return level.getLevel() >= CURRENT_LOG_LEVEL.getLevel();
    }
    
    /**
     * フォーマットされたメッセージでDEBUGログ出力
     * @param format フォーマット文字列
     * @param args フォーマット引数
     */
    public void debug(String format, Object... args) {
        if (isLogLevelEnabled(LogLevel.DEBUG)) {
            debug(String.format(format, args));
        }
    }
    
    /**
     * フォーマットされたメッセージでINFOログ出力
     * @param format フォーマット文字列
     * @param args フォーマット引数
     */
    public void info(String format, Object... args) {
        if (isLogLevelEnabled(LogLevel.INFO)) {
            info(String.format(format, args));
        }
    }
    
    /**
     * フォーマットされたメッセージでWARNログ出力
     * @param format フォーマット文字列
     * @param args フォーマット引数
     */
    public void warn(String format, Object... args) {
        if (isLogLevelEnabled(LogLevel.WARN)) {
            warn(String.format(format, args));
        }
    }
    
    /**
     * フォーマットされたメッセージでERRORログ出力
     * @param format フォーマット文字列
     * @param args フォーマット引数
     */
    public void error(String format, Object... args) {
        if (isLogLevelEnabled(LogLevel.ERROR)) {
            error(String.format(format, args));
        }
    }
}
