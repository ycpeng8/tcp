import java.util.*;
import java.io.*;

public class StudentNetworkSimulator extends NetworkSimulator 
{
    /*
     * Predefined Constants (static member variables):
     *
     *   int MAXDATASIZE : the maximum size of the Message data and
     *                    Packet payload
     *
     *   int A           : a predefined integer that represents entity A
     *   int B           : a predefined integer that represents entity B 
     *
     * Predefined Member Methods:
     *
     *  void stopTimer(int entity): 
     *       Stops the timer running at "entity" [A or B]
     *  void startTimer(int entity, double increment): 
     *       Starts a timer running at "entity" [A or B], which will expire in
     *       "increment" time units, causing the interrupt handler to be
     *       called.  You should only call this with A.
     *  void toLayer3(int callingEntity, Packet p)
     *       Puts the packet "p" into the network from "callingEntity" [A or B]
     *  void toLayer5(String dataSent)
     *       Passes "dataSent" up to layer 5
     *  double getTime()
     *       Returns the current time in the simulator.  Might be useful for
     *       debugging.
     *  int getTraceLevel()
     *       Returns TraceLevel
     *  void printEventList()
     *       Prints the current event list to stdout.  Might be useful for
     *       debugging, but probably not.
     *
     *
     * Predefined Classes:
     *
     *  Message: Used to encapsulate a message coming from layer 5
     *    Constructor:
     *      Message(String inputData): 
     *          creates a new Message containing "inputData"
     *    Methods:
     *      boolean setData(String inputData):
     *          sets an existing Message's data to "inputData"
     *          returns true on success, false otherwise
     *      String getData():
     *          returns the data contained in the message
     *  Packet: Used to encapsulate a packet
     *    Constructors:
     *      Packet (Packet p):
     *          creates a new Packet that is a copy of "p"
     *      Packet (int seq, int ack, int check, String newPayload)
     *          creates a new Packet with a sequence field of "seq", an
     *          ack field of "ack", a checksum field of "check", and a
     *          payload of "newPayload"
     *      Packet (int seq, int ack, int check)
     *          chreate a new Packet with a sequence field of "seq", an
     *          ack field of "ack", a checksum field of "check", and
     *          an empty payload
     *    Methods:
     *      boolean setSeqnum(int n)
     *          sets the Packet's sequence field to "n"
     *          returns true on success, false otherwise
     *      boolean setAcknum(int n)
     *          sets the Packet's ack field to "n"
     *          returns true on success, false otherwise
     *      boolean setChecksum(int n)
     *          sets the Packet's checksum to "n"
     *          returns true on success, false otherwise
     *      boolean setPayload(String newPayload)
     *          sets the Packet's payload to "newPayload"
     *          returns true on success, false otherwise
     *      int getSeqnum()
     *          returns the contents of the Packet's sequence field
     *      int getAcknum()
     *          returns the contents of the Packet's ack field
     *      int getChecksum()
     *          returns the checksum of the Packet
     *      int getPayload()
     *
     */

    /*   Please use the following variables in your routines.
     *   int WindowSize  : the window size
     *   double RxmtInterval   : the retransmission timeout
     *   int LimitSeqNo  : when sequence number reaches this value, it wraps around
     */

    public static final int FirstSeqNo = 0;
    private int WindowSize;
    private double RxmtInterval;
    private int LimitSeqNo;

    // Add any necessary class variables here. Remember, you cannot use
    // these variables to send messages error free! They can only hold
    // state information for A or B.
    // Also add any necessary methods (e.g. checksum of a String)

    /** statistical variables  **/
    private int Num_originalPkt_transBy_A = 0;
    private int Num_retransBy_A = 0;
    private int Num_delivered_to_Layter5_atB = 0;
    private int Num_Ackpkt_sentBy_B = 0;
    private int Num_corrupted_pkt = 0;
    private double loss_ratio = 0;
    private double corrupted_ratio = 0;
    private double avg_rtt = 0;
    private double avg_communication_time = 0;

    private Map<Integer,Double> rtt_map = new HashMap<Integer,Double>();
    private double rttCount = 0.0;
    private double total_rtt = 0.0;


    private Map<Integer,Double> commun_Map = new HashMap<Integer,Double>();
    private double communCount = 0;
    private double total_commun = 0.0;
    /**  **/

    /** A's states **/

    private LinkedList<Packet> sender_buffer = new LinkedList<>();
    // private Packet[] SWS; //Sender Window
    private int send_base;
    private int next_seq;
    private int LPS; // Last packet sent

    /*
     * B variables and functions
     */
    private int RWS; // receive window size
    private int LPA; // last packet acceptable
    private int NPE; // next packet expected
    private int b_acknum; // b's acknum
    private int b_checksum; // b's checksum
    private LinkedList<Packet> receiver_window = new LinkedList<Packet>(); // window of receiving packets from layer 3

    private void b_send_pkt(int seqnum) {
        b_checksum = seqnum + b_acknum;
        Packet sndpkt = new Packet(seqnum, b_acknum, b_checksum);
        toLayer3(B, sndpkt);
        Num_Ackpkt_sentBy_B++;

        return;
    }

    // output checksum
    private int Checksumming(Packet packet) {
        char[] payload = packet.getPayload().toCharArray();
        int checksum = packet.getSeqnum() + packet.getAcknum();
        for (char c : payload) {
            checksum += (int) c;
        }
        return checksum;
    }

    // check if corrupted
    private boolean isCorrupted(Packet packet) {
        return packet.getChecksum() != Checksumming(packet);
    }

    // This is the constructor. Don't touch!
    public StudentNetworkSimulator(int numMessages, double loss, double corrupt, double avgDelay, int trace, int seed,
            int winsize, double delay) {
        super(numMessages, loss, corrupt, avgDelay, trace, seed);
        WindowSize = winsize;
        LimitSeqNo = winsize * 2; // set appropriately; assumes SR here!
        RxmtInterval = delay;
    }

    // This routine will be called whenever the upper layer at the sender [A]
    // has a message to send. It is the job of your protocol to insure that
    // the data in such a message is delivered in-order, and correctly, to
    // the receiving upper layer.
    protected void aOutput(Message message) {
        // System.out.println("+++++++++++++++++");
        next_seq = sender_buffer.size() % LimitSeqNo;
        // System.out.println("buffer size " + sender_buffer.size());
        //  System.out.println("get next_seq " + next_seq);
        
        Packet sender_packet = new Packet(next_seq, 0, -1, message.getData());
        // System.out.println("get payload" + sender_packet.getPayload());
        sender_packet.setChecksum(Checksumming(sender_packet));
        sender_buffer.add(sender_packet);
        
        // System.out.println("sender buffer size is " + sender_buffer.size());
        // System.out.println("LPS is " + LPS);

       
        // System.out.println("get payload " + sender_buffer.get(LPS).getPayload());
        // System.out.println("Send_base is" + send_base);
        // System.out.println("window siez is " + WindowSize);
        for (; LPS < sender_buffer.size() && LPS < send_base + WindowSize; LPS++) {
            if (sender_buffer.get(LPS) != null) {
                Num_originalPkt_transBy_A++;
                toLayer3(A, sender_buffer.get(LPS));

                rtt_map.put(LPS,getTime());
                // rttCount++;
                commun_Map.put(LPS,getTime());
                // communCount++;


                stopTimer(A);
                startTimer(A, RxmtInterval);
            }
        }

    }

    // This routine will be called whenever a packet sent from the B-side
    // (i.e. as a result of a toLayer3() being done by a B-side procedure)
    // arrives at the A-side. "packet" is the (possibly corrupted) packet
    // sent from the B-side.
    protected void aInput(Packet packet)
    {
        if(Checksumming(packet) == packet.getChecksum()){
            int send_base_Seq = send_base % LimitSeqNo;
            int tmpAck = packet.getSeqnum();
            System.out.println("get ack num "+tmpAck);
            if(send_base_Seq >= WindowSize && tmpAck < WindowSize){
                stopTimer(A);

                int last_send_base = send_base;
                send_base += LimitSeqNo - send_base_Seq + tmpAck;

                /* compute time */

                // total_rtt += getTime() - rtt_map.get(last_send_base);
                rttCount++;

                for(;last_send_base<send_base;last_send_base++){
                    // double tmptime = rtt_map.get(last_send_base);
                    double tmptime = rtt_map.get(send_base-1);
                    if(tmptime != -1.0){
                        total_rtt += getTime() - tmptime;
                        rtt_map.put(last_send_base,-1.0);
                        // rttCount++;
                    }
                    // total_commun += getTime() - commun_Map.get(last_send_base);
                    total_commun += getTime() - commun_Map.get(send_base-1);
                    communCount++;
                }

                // if(commun_Map.containsKey(last_send_base)){
                //     total_commun += getTime() - commun_Map.get(last_send_base);
                //     communCount++;
                // }

                // if(send_base < sender_buffer.size()){
                //     Num_originalPkt_transBy_A++;
                //     toLayer3(A,sender_buffer.get(send_base));

                //     rtt_map.put(send_base,getTime());
                //     // rttCount++;
                //     // commun_Map.put(send_base,getTime());
                //     // communCount++;

                //     stopTimer(A);
                //     startTimer(A, RxmtInterval);
                // }
            }
            else if(tmpAck >= send_base_Seq+1){
                stopTimer(A);
                int last_send_base = send_base;
                send_base += (tmpAck - send_base_Seq) ;
                
                // if(rtt_map.Constants(last_send_base)){
                //     total_rtt += getTime() - rtt_map.get(last_send_base);
                //     rttCount++;
                // }

                rttCount++;
                /* compute time */
                for(;last_send_base<send_base;last_send_base++){
                    // double tmptime = rtt_map.get(last_send_base);
                    double tmptime = rtt_map.get(send_base-1);
                    if(tmptime != -1.0){
                        total_rtt += getTime() - tmptime;
                        rtt_map.put(last_send_base,-1.0);
                        // rttCount++;
                    }
                    // total_commun += getTime() - commun_Map.get(last_send_base);
                    total_commun += getTime() - commun_Map.get(send_base-1);
                    communCount++;
                }

                // if(commun_Map.containsKey(last_send_base)){
                //     total_commun += getTime() - commun_Map.get(last_send_base);
                //     communCount++;
                // }



                // if(send_base < sender_buffer.size()){
                //     Num_originalPkt_transBy_A++;
                //     toLayer3(A,sender_buffer.get(send_base));

                //     rtt_map.put(send_base,getTime());
                //     // rttCount++;
                //     // commun_Map.put(send_base,getTime());
                //     // communCount++;

                //     stopTimer(A);
                //     startTimer(A, RxmtInterval);
                // }

            }
            else{
                if(send_base < sender_buffer.size()){
                    toLayer3(A,sender_buffer.get(send_base));

                    rtt_map.put(send_base,getTime());

                    stopTimer(A);
                    startTimer(A, RxmtInterval);
                    Num_retransBy_A++;
                }
            }
            // if(packet.getAcknum() >= send_base+1){
            //     stopTimer(A);
                
                
            //     send_base = packet.getAcknum();
            //     // LPS=send_base+WindowSize-1;
            // }
        }
        else{
            Num_corrupted_pkt++;
        }

    }

    // This routine will be called when A's timer expires (thus generating a
    // timer interrupt). You'll probably want to use this routine to control
    // the retransmission of packets. See startTimer() and stopTimer(), above,
    // for how the timer is started and stopped.
    protected void aTimerInterrupt() {
        System.out.println("Timeout!");
        toLayer3(A, sender_buffer.get(send_base));

        rtt_map.put(send_base,getTime());

        stopTimer(A);
        startTimer(A, RxmtInterval);
        Num_retransBy_A++;
            

    }

    // This routine will be called once, before any of your other A-side
    // routines are called. It can be used to do any required
    // initialization (e.g. of member variables you add to control the state
    // of entity A).
    protected void aInit() {

        // SWS = new Packet[WindowSize];
        send_base = 0;
        next_seq = 0;
        LPS = 0;
        LimitSeqNo = 2 * WindowSize;

    }

    // This routine will be called whenever a packet sent from the A-side
    // (i.e. as a result of a toLayer3() being done by an A-side procedure)
    // arrives at the B-side. "packet" is the (possibly corrupted) packet
    // sent from the A-side.
    protected void bInput(Packet packet)
    {
        // if packet is corrupted
        if (isCorrupted(packet))
        {
            System.out.println("bInput(): B getting a corrupted pkt");
            Num_corrupted_pkt++;
            return;
        }

        System.out.println("bInput(): B getting pkt" + packet.getSeqnum() + ", expecting pkt" + NPE);

        int this_seqnum = packet.getSeqnum();
        // if this is not a stop-and-wait model and the packet is in the receiver window 
        if (((NPE < LPA) && (this_seqnum >= NPE) && (this_seqnum <= LPA)) || ((NPE > LPA) && (this_seqnum >= NPE || this_seqnum <= LPA)))
        {
            // if the size of rcvwin is 0, the operation will be easy and I take it out as one part
            if (receiver_window.size() == 0)
            {
                if (this_seqnum == NPE)
                {
                    NPE = (NPE + 1) % LimitSeqNo;
                    LPA = (LPA + 1) % LimitSeqNo;
                    b_send_pkt(NPE);
                    toLayer5(packet.getPayload());
                    Num_delivered_to_Layter5_atB++;
                    return;
                }
                else
                {
                    receiver_window.add(packet);
                    b_send_pkt(NPE);
                    return;
                }
            }
            // if the size of rcvwin is not 0
            else
            {
                int length = receiver_window.size();
                // put the packet into rcvwin
                for (int i = 0; i < length; i++)
                {
                    // if packet is duplicate, drop it and send an ACK
                    if (this_seqnum == receiver_window.get(i).getSeqnum())
                    {
                        System.out.println("bInput(): getting a duplicate pkt" + this_seqnum);
                        b_send_pkt(NPE);
                        return;
                    }
                    
                    // if packet is new, put it into rcvwin in order
                    if (NPE < LPA)
                    {
                        if (this_seqnum < receiver_window.get(i).getSeqnum())
                        {
                            receiver_window.add(i, packet);
                            break;
                        }
                    }
                    else
                    {
                        if (((this_seqnum >= NPE) && (receiver_window.get(i).getSeqnum() > NPE) && (this_seqnum < receiver_window.get(i).getSeqnum()))
                        || ((this_seqnum >= NPE) && (receiver_window.get(i).getSeqnum() <= LPA))
                        || ((this_seqnum <= LPA) && (receiver_window.get(i).getSeqnum() <= LPA) && (this_seqnum < receiver_window.get(i).getSeqnum())))
                        {
                            receiver_window.add(i, packet);
                            break;
                        }
                    }
                }

                if (receiver_window.size() == length)
                {
                    receiver_window.addLast(packet);
                }

                // test
                System.out.print("bInput(): receiver window: ");
                for (int i = 0; i < receiver_window.size(); i++)
                {
                    System.out.print(receiver_window.get(i).getSeqnum() + " ");
                }
                System.out.println();

                /* 
                 * iterate the rcvwin. 
                 * if B has the packet of NPE, send its ACK
                 * if not, stop iterate and send ACK NPE 
                 */
                while (receiver_window.size() != 0)
                {
                    if (receiver_window.get(0).getSeqnum() != NPE)
                    {
                        b_send_pkt(NPE);
                        return;
                    }

                    NPE = (NPE + 1) % LimitSeqNo;
                    LPA = (LPA + 1) % LimitSeqNo;
                    toLayer5(receiver_window.get(0).getPayload());
                    Num_delivered_to_Layter5_atB++;
                    receiver_window.remove();
                }
                b_send_pkt(NPE);
                return;
            }
            
        }
        // if this is a stop-and-wait model
        else if (NPE == LPA)
        {
            if (this_seqnum == NPE)
            {
                NPE = (NPE + 1) % LimitSeqNo;
                LPA = (LPA + 1) % LimitSeqNo;
                toLayer5(packet.getPayload());
                Num_delivered_to_Layter5_atB++;
                b_send_pkt(NPE);
            }
            else
            {
                b_send_pkt(NPE);
            }
        }
        // if packet is out of receiver_window
        else
        {
            b_send_pkt(NPE);
        }
        return;
    }

    // This routine will be called once, before any of your other B-side
    // routines are called. It can be used to do any required
    // initialization (e.g. of member variables you add to control the state
    // of entity B).
    protected void bInit() {
        RWS = WindowSize;
        NPE = 0;
        LPA = RWS + NPE - 1;
        b_acknum = 1;
    }

    // Use to print final statistics
    protected void Simulation_done() {
        // TO PRINT THE STATISTICS, FILL IN THE DETAILS BY PUTTING VARIBALE NAMES. DO
        // NOT CHANGE THE FORMAT OF PRINTED OUTPUT
        double  Ratio_lost = 0;
        if(Num_retransBy_A - Num_corrupted_pkt < 0){
            Ratio_lost = 0;
        }else{
            Ratio_lost = (double)(Num_retransBy_A - Num_corrupted_pkt)/(double)((Num_originalPkt_transBy_A+Num_retransBy_A)+Num_Ackpkt_sentBy_B);
        }
        double  Ratio_corrupted = (double)Num_corrupted_pkt / (double)((Num_originalPkt_transBy_A+Num_retransBy_A)+ Num_Ackpkt_sentBy_B-(Num_retransBy_A-Num_corrupted_pkt));
        System.out.println("\n\n===============STATISTICS=======================");
        System.out.println("Number of original packets transmitted by A:" + Num_originalPkt_transBy_A);
        System.out.println("Number of retransmissions by A:" + Num_retransBy_A);
        System.out.println("Number of data packets delivered to layer 5 at B:" + Num_delivered_to_Layter5_atB);
        System.out.println("Number of ACK packets sent by B:" + Num_Ackpkt_sentBy_B);
        System.out.println("Number of corrupted packets:" + Num_corrupted_pkt);
        System.out.println("Ratio of lost packets:" + Ratio_lost);
        System.out.println("Ratio of corrupted packets:" + Ratio_corrupted);
        System.out.println("Average RTT:" + total_rtt/rttCount);
        System.out.println("Average communication time:" + total_commun/communCount);
        System.out.println("==================================================");

        // PRINT YOUR OWN STATISTIC HERE TO CHECK THE CORRECTNESS OF YOUR PROGRAM
        // EXAMPLE GIVEN BELOW
        // System.out.println("\nEXTRA:");
        // System.out.println("All rtt:" + total_rtt);
        // System.out.println("counter for rtt:" + rttCount);
        // System.out.println("All communication time:" + total_commun);
        // System.out.println("counter for communication:" + communCount);
        // System.out.println("Example statistic you want to check e.g. number of ACK packets received by A :" + "<YourVariableHere>");
    }

}