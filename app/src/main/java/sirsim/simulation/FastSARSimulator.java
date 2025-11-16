package sirsim.simulation;

import sirsim.network.Graph;

import java.util.*;

public final class FastSARSimulator {
    public enum Status { S, A, R }
    public enum EventType { TRANSMIT, RECOVER }

    private static final class Event {
        final double time;
        final int node;
        final EventType type;

        final long seq;
        Event(double time, int node, EventType type, long seq) {
            this.time = time;
            this.node = node;
            this.type = type;
            this.seq = seq;
        }
    }

    private final Graph g;
    private final double lambda;
    private final double gamma;
    private final double tMax;
    private final int[] thresholdList;
    private final double alpha;
    private final double beta;
    private final SplittableRandom rng;

    private final Status[] status;
    private final double[] predInfTime;
    private final double[] recTime;
    private final double[] tInfect;
    private final double[] tRecover;

    private final ArrayList<Double> times = new ArrayList<>();
    private final int[] infectedCount;
    private final ArrayList<Integer> S = new ArrayList<>();
    private final ArrayList<Integer> A = new ArrayList<>();
    private final ArrayList<Integer> R = new ArrayList<>();

    private int Scount, Acount, Rcount;

    public FastSARSimulator(Graph g, double lambda, double gamma, double tMax, int[] thresholdList, double alpha, double beta, long seed) {
        if (g == null) throw new IllegalArgumentException("Graph is null");
        if (lambda < 0 || gamma < 0) throw new IllegalArgumentException("lambda and gamma must be non-negative");
        if (tMax <= 0) throw new IllegalArgumentException("tMax must be positive");
        if (thresholdList == null || thresholdList.length != g.n) throw new IllegalArgumentException("thresholdList must be an array of length n");
        this.g = g;
        this.lambda = lambda;
        this.gamma = gamma;
        this.tMax = tMax;
        this.thresholdList = thresholdList;
        this.alpha = alpha;
        this.beta = beta;
        this.rng = new SplittableRandom(seed);

        int n = g.n;
        this.status = new Status[n];
        this.predInfTime = new double[n];
        this.recTime = new double[n];
        this.tInfect = new double[n];
        this.tRecover = new double[n];

        Arrays.fill(tInfect, Double.NaN);
        Arrays.fill(tRecover, Double.NaN);
        this.infectedCount = new int[n];
    }

    public SarResult run(int[] initialInfecteds) {
        final int n = g.n;
        Scount = n;
        Acount = 0;
        Rcount = 0;
        
        for (int u = 0; u < n; u++){
            status[u] = Status.S;
            predInfTime[u] = Double.POSITIVE_INFINITY;
            recTime[u] = Double.POSITIVE_INFINITY;
        }

        record(0.0);

        final Comparator<Event> cmp = (a, b) -> {
            int c = Double.compare(a.time, b.time);
            if (c != 0) return c;
            c = a.type.compareTo(b.type);
            if (c != 0) return c;
            return Long.compare(a.seq, b.seq);
        };

        final PriorityQueue<Event> Q = new PriorityQueue<>(cmp);
        final SeqGen seqGen = new SeqGen() {
            private long seq = 0L;
            @Override
            public long next() { return seq++; }
        };
        

        // 初期感染者の投入
        boolean[] seen = new boolean[n];
        for (int u : initialInfecteds) {
            if (u < 0 || u >= n) throw new IllegalArgumentException("invalid initial infected: " + u);
            if (seen[u]) continue;
            seen[u] = true;
            predInfTime[u] = 0.0;
            Q.add(new Event(0.0, u, EventType.TRANSMIT, seqGen.next()));
        }

        while (!Q.isEmpty()) {
            Event ev = Q.poll();
            final int u = ev.node;
            final double t = ev.time;

            if (t >= tMax) break;

            if (ev.type == EventType.TRANSMIT) {
                if (status[u] == Status.S && t == predInfTime[u]) {
                    processTransmit(u, t, Q, () -> seqGen.next());
                }
            } else { // EventType.RECOVER
                if (status[u] == Status.A && t == recTime[u]) {
                    processRecover(u, t);
                }
            }
        }

        return new SarResult(n, times, S, A, R, tInfect, tRecover);
    }

    private void processTransmit(int u, double t, PriorityQueue<Event> Q, SeqGen seqGen) {
        infectedCount[u]++;
        if (infectedCount[u] >= thresholdList[u]) {
            Scount--; Acount++;
            record(t);

            status[u] = Status.A;
            tInfect[u] = t;

            double tRec = t + exp(rng, gamma);
            recTime[u] = tRec;
            if (tRec < tMax) {
                Q.add(new Event(tRec, u, EventType.RECOVER, seqGen.next()));
            }

            for (int e = g.firstArc(u); e < g.endArc(u); e++) {
                int v = g.colIdx[e];
                findTransmit(Q, t, u, v, seqGen, alpha, beta);
            }

        }
    }

    private void findTransmit(PriorityQueue<Event> Q, double t, int source, int target, SeqGen seqGen, double alpha, double beta) {
        if (status[target] != Status.S) return;

        int k = g.degree(source);
        int kp = g.degree(target);

        double lambdaF = lambda * Math.pow(k == 0 ? 1.0 : k , alpha) * Math.pow(kp == 0 ? 1.0 : kp, beta);

        if (lambdaF == 0.0) return;

        double tInf = t + exp(rng, lambdaF);
        double bound = Math.min(recTime[source], Math.min(predInfTime[target], tMax));
        if (tInf < bound) {
            predInfTime[target] = tInf;
            Q.add(new Event(tInf, target, EventType.TRANSMIT, seqGen.next()));
        }
    }

    private void processRecover(int u, double t) {
        Acount--; Rcount++;
        record(t);

        status[u] = Status.R;
        tRecover[u] = t;
    }

    private interface SeqGen { long next(); }

    private static double exp(SplittableRandom rng, double rate) {
        if (rate <= 0.0) return Double.POSITIVE_INFINITY;

        double u = 1.0 - rng.nextDouble();
        return -Math.log(u) / rate;
    }

    private void record(double t) {
        times.add(t);
        S.add(Scount);
        A.add(Acount);
        R.add(Rcount);
    }

    public static SarResult simulate(Graph g, double lambda, double gamma, double tMax, int[] thresholdList, double alpha, double beta, int[] initialInfecteds, long seed) {
        return new FastSARSimulator(g, lambda, gamma, tMax, thresholdList, alpha, beta, seed).run(initialInfecteds);
    }
}
