package sirsim.utils;

/**
 * Loggerクラスのテスト用サンプル
 */
public class LoggerTest {
    private static final Logger logger = new Logger(LoggerTest.class);
    
    public static void main(String[] args) {
        // 各ログレベルのテスト
        logger.debug("これはDEBUGレベルのログです");
        logger.info("これはINFOレベルのログです");
        logger.warn("これはWARNレベルのログです");
        logger.error("これはERRORレベルのログです");
        
        // フォーマット機能のテスト
        logger.debug("デバッグ情報: 値=%d, 文字列=%s", 42, "テスト");
        logger.info("情報: 進捗 %.1f%% 完了", 75.5);
        logger.warn("警告: ファイル %s が見つかりません", "test.txt");
        logger.error("エラー: コード %d で失敗しました", 404);
        
        // ログレベル確認
        System.out.println("現在のログレベル: " + Logger.getCurrentLogLevel());
        System.out.println("DEBUG有効: " + Logger.isLogLevelEnabled(Logger.LogLevel.DEBUG));
        System.out.println("INFO有効: " + Logger.isLogLevelEnabled(Logger.LogLevel.INFO));
        System.out.println("WARN有効: " + Logger.isLogLevelEnabled(Logger.LogLevel.WARN));
        System.out.println("ERROR有効: " + Logger.isLogLevelEnabled(Logger.LogLevel.ERROR));
    }
}
