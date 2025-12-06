package sirsim;

import sirsim.network.Graph;
import sirsim.network.topology.RR;

public class App {
    public static void main(String[] args) {
        int N = 10000;
        int d = 10;
        Graph g = RR.generateRR(N, d, 12345L);
        g.printInfo();
        for (int neighbor : g.neighbors(0)) {
            System.out.println(neighbor);
        }
    }
}
