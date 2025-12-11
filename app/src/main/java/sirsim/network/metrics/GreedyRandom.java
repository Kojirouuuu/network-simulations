package sirsim.network.metrics;

import java.io.File;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Random;
import java.util.Scanner;

public class GreedyRandom {
	static int n; // 頂点数
	static int l; // 辺数
	static int[][] pairList; // 大きさl× 2 のint 型配列。配列要素pairList[j][0] とpairList[j][1] には、j番目の辺で繋がる２つの頂点の頂点番号をそれぞれ格納する。
	static int[] nList;//左右両方の頂点をすべて同じリストに入れて、頂点数を数える。
	static int[] degreeList; //長さn のint型配列。配列要素degreeList[j] には頂点jの次数を格納する。
	static int[] communitylabel;//j番目の頂点が所属するコミュニティ番号を格納する。
	static int[][] communitylabelrecord;
	static double[] record;
	static int[][] Nrecord;
	
	public static void main(String[] args) throws Exception {
		Scanner fileScanner = null;
		String inFileNameBody = "app/src/main/java/sirsim/network/files/exampleNetworkN24";
		String inFileName = inFileNameBody+".txt";
		
        fileScanner = new Scanner(new File(inFileName));
        String lineBuffer = fileScanner.nextLine().trim();
        lineBuffer = lineBuffer.replace("%", "").replace(",", "");
		String[] parts = lineBuffer.split("\\s+");
		n = Integer.parseInt(parts[0]);
		l = Integer.parseInt(parts[2]);
		
		int i=0;
		pairList= new int[l][2];
		nList=new int[2*l];
		while (fileScanner.hasNextLine()) {//1行ずつ見ていく。
			int v = Integer.parseInt(fileScanner.next());
			String ww = fileScanner.nextLine();
			int w = Integer.parseInt(ww.trim());
			//System.out.println(i+"番目の辺　"+"v="+v+", w="+w);
			pairList[i][0] = v;//0列目に左頂点を入れる
			pairList[i][1] = w;//1行目に右頂点を入れる
			nList[2*i]=v;//偶数に左頂点
			nList[2*i+1]=w;//奇数に右頂点
			i=i+1;
		}
		fileScanner.close();
		
		//printMatrix(pairList);
		//printList(nList);
		
		System.out.println("n="+n);
		System.out.println("l="+l);
		
		degreeList= new int[n];
		for(int j1=0; j1<n; j1++) {
			int degree=0;
			for(int j2=0; j2<nList.length; j2++) {
				if(nList[j2]==j1) {//頂点kがnList内に何個あるかを数える
					degree++;
				}
			}
			degreeList[j1]=degree;
		}
		//System.out.print("次数列：");
		//printList(degreeList);
		
		record = new double[n];
		for(int jn=0; jn<n; jn++) {
			record[0] += -((double)degreeList[jn]*(double)degreeList[jn])/(4.0*(double)l*(double)l);
		}
		Nrecord = new int [n][2];
		communitylabelrecord = new int[n][n];
		
		//コミュニティ作り
		communitylabel = new int[n];
		for (int j = 0; j < n; j++) {
		    communitylabel[j] = j;
		    communitylabelrecord[0][j]=j;
		}
		
		//モジュラリティの計算
		int u =1;
		while(u<n) {
			System.out.print("u="+u+"：");
			double[][] result = modularity(n,l,pairList,degreeList,communitylabel);
			//for (int jp = 0; jp < result.length; jp++) {System.out.println((int)result[jp][0]+" "+(int)result[jp][1]+" "+result[jp][2]);}
	        double[][] sortresult = new double[result.length][result[0].length];
	        double[] result3 = new double[result.length];
	        for (int jr = 0; jr < result.length; jr++) {
	        	sortresult[jr] = Arrays.copyOf(result[jr], result[jr].length);
	        	result3[jr] = result[jr][2];
	        }
	        Arrays.sort(sortresult, Comparator.comparingDouble(row -> row[2]));
			//for (int jp = 0; jp < sortresult.length; jp++) {System.out.println((int)sortresult[jp][0]+" "+(int)sortresult[jp][1]+" "+sortresult[jp][2]);}
	        //System.out.println("MaxList(result3)="+MaxList(result3));
	        int randcount = 0;
	        double[][] randomList = new double[l][3];
			for (int j1 = 0; j1 < sortresult.length; j1++) {
				if(sortresult[j1][2]==MaxList(result3)) {
					randomList[randcount][0]=sortresult[j1][0];
					randomList[randcount][1]=sortresult[j1][1];
					randomList[randcount][2]=sortresult[j1][2];
					randcount++;
				}
			}
			Random rand = new Random();
			int Maxnumber=rand.nextInt(randcount);
			//乱数を基に選べるようにする。
			System.out.println("sortresult["+Maxnumber+"][0]="+(int)randomList[Maxnumber][0]+", sortresult["+Maxnumber+"][1]="+(int)randomList[Maxnumber][1]+", sortresult["+Maxnumber+"][2]="+randomList[Maxnumber][2]);
			Nrecord[u][0] = (int)randomList[Maxnumber][0];
			Nrecord[u][1] = (int)randomList[Maxnumber][1];
	    	communitylabel[(int)randomList[Maxnumber][1]] = (int)randomList[Maxnumber][0];
	    	for (int j2 = 0; j2 < n; j2++) {
				//System.out.println("communitylabel["+j2+"]="+communitylabel[j2]+", sortresult["+Maxnumber+"][1]="+(int)sortresult[Maxnumber][1]);   		
	    		if (communitylabel[j2] == (int)randomList[Maxnumber][1]) {
			    	communitylabel[j2] = (int)randomList[Maxnumber][0];
	    		}
	    	}
	    	//System.out.print("u="+u+"のラベル：");
			//printList(communitylabel);
			record[u] = MaxList(result3)+record[u-1];
			communitylabelrecord[u] = Arrays.copyOf(communitylabel, n);
			//System.out.println();
			u++;
			//if(u-1==1) {break;}
		}
		/*System.out.print("記録列：");
		printdoubleList(record);
		System.out.println("コミュニティラベル変遷");
		printMatrix(communitylabelrecord);
		System.out.println();*/
		
		//Mの最大値のところを探す。
		int y = 0;
		for (int j = 0; j < n; j++) {
			if(record[j]==MaxList(record)) {
				y = j;
		    	break;
			}
		}
		
		System.out.println();
		System.out.println("最適分割："+y+"回目");
		System.out.println("モジュラリティ："+record[y]);
		System.out.print("ラベル：");
		printList(communitylabelrecord[y]);
		System.out.println();
		
		int[] communitysize = new int [n];
		for (int j = 0; j < n; j++) {
			communitysize[communitylabelrecord[y][j]]++;
		}
		int communitycount = 1;
		for (int j1 = 0; j1 < n; j1++) {
			if(communitysize[j1]!=0) {
				System.out.print("コミュニティNo."+communitycount+"：");
				for (int j2 = 0; j2 < n; j2++) {
					if(communitylabelrecord[y][j2] == j1) {
						System.out.print(j2+" ");
					}
				}
				System.out.println();
				communitycount++;
			}
		}
		System.out.println();
		
		//ファイルの書き出し
		PrintWriter filePrinter1 = null;//情報基礎第14回より
			String outFolderName1 = "出力ファイル\\\\greedy法データ\\\\";
			String outFileName1 = "GreedyEdgeRandomData_"+inFileNameBody+".csv";
			filePrinter1 = new PrintWriter(new File(outFolderName1+outFileName1),"Shift-JIS");//文字コードがcp932になってしまいUTF-8にならないため、強制的にさせる
			filePrinter1.println("step,merged_r,merged_s,Q_after_float");
			for(int pn=0; pn<n; pn++) {
				filePrinter1.println(pn+","+Nrecord[pn][0]+","+Nrecord[pn][1]+","+record[pn]);//値の区切りとしてカンマを入れる
			}
		filePrinter1.close();
		System.out.println("出力ファイル："+outFileName1);
		PrintWriter filePrinter2 = null;
			String outFolderName2 = "出力ファイル\\\\greedy法\\\\";
			String outFileName2 = "GreedyEdgeRandom_"+inFileNameBody+".csv";
			filePrinter2 = new PrintWriter(new File(outFolderName2+outFileName2),"Shift-JIS");
			communitycount = 1;
			for (int j1 = 0; j1 < n; j1++) {
				if(communitysize[j1]!=0) {
					filePrinter2.print("コミュニティNo."+communitycount+"："+",");
					for (int j2 = 0; j2 < n; j2++) {if(communitylabelrecord[y][j2] == j1) {filePrinter2.print(j2+",");}}
					filePrinter2.println();
					communitycount++;
				}
			}
			filePrinter2.println();
			filePrinter2.println("モジュラリティ："+","+record[y]);
			filePrinter2.println();
			for (int jn = 0; jn < n; jn++) {filePrinter2.println(jn+","+communitylabelrecord[y][jn]);}
		filePrinter2.close();
		System.out.println("出力ファイル："+outFileName2);
	}
	
	static double[][] modularity(int n,int l,int[][] pair,int[] degree ,int[] label) {//モジュールの計算をするメソッド
	    double[] tot = new double[n];
	    for (int i = 0; i < n; i++) tot[i] = 0;
	    for (int i = 0; i < n; i++) {
	        int c = label[i];
	        tot[c] += degree[i];
	    }
		int[][] copypair= new int[l][2];for(int jp1 = 0; jp1 < pair.length; jp1++) {copypair[jp1] = pair[jp1].clone();}
		//ペアリストを書き換え
        for (int i = 0; i < n; i++) {
            if (i!=label[i]) {
            	for (int j = 0; j < copypair.length; j++) {
                    if (i==copypair[j][0]) {copypair[j][0]=label[i];}
                    if (i==copypair[j][1]) {copypair[j][1]=label[i];}
                }
            }
        }
		//大きい方を2列目にし、重複する組合せを削除。
        for (int i = 0; i < copypair.length; i++) {
            if (copypair[i][0] > copypair[i][1]) {
                int tmp = copypair[i][0];
                copypair[i][0] = copypair[i][1];
                copypair[i][1] = tmp;
            }
            for (int j = i + 1; j < copypair.length; j++) {
                if (copypair[i][0] == copypair[j][0] && copypair[i][1] == copypair[j][1]) {
                    copypair[j][0] = n*2;
                    copypair[j][1] = n*2;
                }
            }
        }
		int edgecount = 0;//コミュニティ外に向かう辺の個数を計算
		for(int jl = 0; jl < l; jl++) {
			if(copypair[jl][0]!=copypair[jl][1]) {edgecount++;}
		}
		double[][] r=new double[edgecount][3];
		int t = 0;
		for (int jl = 0; jl < l; jl++) {
			int c1 = copypair[jl][0];
			int c2 = copypair[jl][1];
			if(c1 > c2) {
				int ec= c2;
				c2 = c1;
				c1 = ec;
			}
			if(c1 != c2) {
	            double e_c1c2 = 0.0;
	            for (int j = 0; j < pair.length; j++) {
	                if (label[pair[j][0]] == c1 && label[pair[j][1]] == c2) e_c1c2 += 1;
	                if (label[pair[j][0]] == c2 && label[pair[j][1]] == c1) e_c1c2 += 1;
	            }
	            double deltaQ = ( e_c1c2 - (tot[c1] * tot[c2]) / (2.0 * l) ) / l;
	            //System.out.println("c1="+c1+", c2="+c2+", e_c1c2="+e_c1c2+", tot[c1]="+tot[c1]+", tot[c2]="+tot[c2]+", deltaQ="+deltaQ);
				r[t][0]=c1;
				r[t][1]=c2;
				r[t][2]=deltaQ;
				t++;
			}
		}
		return r;
	}
	
	static int countZero(int[] r){
		int count = 0;
		for(int p=0; p<r.length; p++) {
			if(r[p]==0){
				count++;//最大値を更新
			}
		}
		return count;
	}
	
	static double MaxList(double[] r){//リストの最大値
		double max = -1.79769E+308;
		//System.out.println("r.length="+r.length);
		for(int p=0; p<r.length; p++) {
			//System.out.println("max="+max+", r[p]="+r[p]);
			if(max<=r[p]){
				max=r[p];//最大値を更新
				//System.out.println("作動");
			}
		}
		return max;
	}    
	
    static void printMatrix(int[][] r){//インスタントメソッドで2次元の配列を表示する
        for(int p=0; p<r.length; p++){
            for(int q=0; q<r[p].length; q++)
                System.out.printf("%3d",r[p][q]);
            System.out.println();
        }
    }
    
    static void printdoubleMatrix(double[][] r){//インスタントメソッドで2次元の配列を表示する
        for(int p=0; p<r.length; p++){
            for(int q=0; q<r[p].length; q++)
                System.out.printf("%3d",r[p][q]);
            System.out.println();
        }
    }
    
    static void printList(int[] r){//インスタントメソッドで配列を表示する
    	int s=r.length;
		for(int j=0; j<s-1; j++) {
			System.out.print(r[j]+", ");
		}
		System.out.println(r[s-1]);
    }
    
    static void printdoubleList(double[] r){//インスタントメソッドで配列を表示する
    	int s=r.length;
		for(int j=0; j<s-1; j++) {
			System.out.print(r[j]+", ");
		}
		System.out.println(r[s-1]);
    }
}