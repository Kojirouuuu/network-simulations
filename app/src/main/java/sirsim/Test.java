package sirsim;

import sirsim.network.Graph;
import sirsim.network.topology.Config;
import java.nio.file.Paths;
import java.io.IOException;
import java.nio.file.Path;

public class Test {
    public static void main(String[] args) throws IOException {
        int[] NList = {10000, 40000, 100000};
        double[] gammaList = {2.1, 2.5, 3.0};
        Path basePath = Paths.get("out/pk/config");

        for (int N : NList) {
            for (double gamma : gammaList) {
                Graph g = Config.generatePowerLawConfig(N, gamma);
                g.writeEdgelist(basePath.resolve(String.format("N=%d/gamma=%.1f/edgelist.txt", N, gamma)));
            }
        }
    }
}
