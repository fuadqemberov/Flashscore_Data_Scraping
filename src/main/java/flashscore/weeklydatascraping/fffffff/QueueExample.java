package flashscore.weeklydatascraping.fffffff;

public class QueueExample {
    Node front;
    Node rear;
    int size;
    int count;
   //FIFO
    public QueueExample(int size) {
        this.size = size;
        rear = null;
        front = null;
        count = 0;
    }

    public void enQueue(int data) {

        if (isFull()) {
            System.out.println("Queue is Full !");
        } else {
            Node eleman = new Node(data);
            if (isEmpty()) {
                front = rear = eleman;
            } else {
                rear.next = eleman;
                rear = eleman;
            }
            count++;
        }
    }

    public int deQueue(){
        int dequed = 0;
        if(isEmpty()){
            System.out.println("Queue is Empty !");
        }else {
            System.out.println("Data is deqeued : "+front.data);
            dequed = front.data;
            front = front.next;
            count--;
        }
        return dequed;
    }

    public boolean isFull(){
        return  count == size;
    }

    public boolean isEmpty(){
        return count == 0;
    }

    public void print(){
        if(isEmpty()){
            System.out.printf("Empty !");
        }else {
            Node temp = front;
            System.out.print("begin -> ");
            while (temp != null){
                System.out.print(temp.data+" -> ");
                temp = temp.next;
            }
            System.out.print("last");
        }

    }

}
