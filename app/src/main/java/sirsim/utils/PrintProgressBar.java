package sirsim.utils;

public final class PrintProgressBar {
    private PrintProgressBar() { /* no instances */ }

    public static void printProgressBar(int current, int total) {
    int barLength = 40; // プログレスバーの長さ
    double percent = (double) current / total;
    int filled = (int) (percent * barLength);

    StringBuilder bar = new StringBuilder();
    bar.append("\r["); // 行頭に戻る

    for (int i = 0; i < barLength; i++) {
        if (i < filled) bar.append('#');
        else bar.append('-');
    }

    bar.append("] ");
    bar.append(String.format("%3d%%", (int)(percent * 100)));

    System.out.print(bar.toString());
    }
}
