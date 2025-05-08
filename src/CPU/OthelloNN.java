package CPU;
import java.util.Arrays;

public class OthelloNN {

    double[][] W1, W2;
    
    double[] b1, b2;
    
    
    
    public OthelloNN(double[][] W1, double[] b1, double[][] W2, double[] b2) {
        
        this.W1 = W1; this.b1 = b1;
        
        this.W2 = W2; this.b2 = b2;
    
    }
    
    
    
    public double[] predict(double[] board) {
    
        double[] h1 = relu(dot(W1, board, b1));
        
        double[] out = dot(W2, h1, b2);
        
        return softmax(out);
    
    }
    
    
    
    private double[] dot(double[][] W, double[] x, double[] b) {
    
        double[] result = new double[W.length];
        
        for (int i = 0; i < W.length; i++) {
        
            result[i] = b[i];
            
            for (int j = 0; j < x.length; j++)
            
            result[i] += W[i][j] * x[j];
        
        }
        
        return result;
    
    }
    
    
    
    private double[] relu(double[] x) {
    
        double[] out = new double[x.length];
        
        for (int i = 0; i < x.length; i++)
        
            out[i] = Math.max(0, x[i]);
        
        return out;
    
    }
    
    
    
    private double[] softmax(double[] x) {
    
        double max = Arrays.stream(x).max().getAsDouble();
        
        double sum = 0.0;
        
        double[] exps = new double[x.length];
        
        for (int i = 0; i < x.length; i++) {
        
            exps[i] = Math.exp(x[i] - max);
            
            sum += exps[i];
        
        }
        
        for (int i = 0; i < x.length; i++) {
        
            exps[i] /= sum;
        
        }
        
        return exps;
    
    }
    
    }