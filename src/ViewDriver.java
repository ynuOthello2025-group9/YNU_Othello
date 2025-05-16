
public class ViewDriver {

/* TO TEST:
 * 1) transition screen (main -> player/cpu -> game screen -> main)
 * 2) each screen button functions (+ message output if possible)
 * 
 * 
 * 
*/
    public static void main(String[] args) {

        View testview=new View();
        Client testclient=new Client(testview,"test address",1234);
        testview.setClient(testclient);
        testview.setVisible(true);
        System.out.println("[ViewDriver]: success");
        
    }
}
