package flashscore.weeklydatascraping.fffffff;

public class MyStack {
    int size;
    Node top;
    int count;

    public MyStack(int size) {
        this.size = size;
        count = 0;
        top = null;
    }

    public void push(int data) {

        Node eleman = new Node(data);
        if(isFull()){
            System.out.println( "Stack is full");
        }
        else {
            if(isEmpty()){
                top =  eleman;
                System.out.println("First element added : "+eleman.data);
            }
            else{
                eleman.next = top;
                top = eleman;
                System.out.println("Element added : "+eleman.data);
            }
            count++;
        }

    }

    public int pop() {
        int poped = 0;
        if(isEmpty()){
            System.out.println("Stack is Empty ! ");
        }else{
            System.out.println("Data poped  : "+top.data);
            poped = top.data;
            top = top.next;
            count--;
        }
        return poped;
    }

    public int peek() {
        if(isEmpty()){
            System.out.println("Stack is Empty ! ");
        }else{
            return top.data;
        }
        return 0;
    }

    public boolean isEmpty() {
        return count == 0;
    }

    public boolean isFull() {
        return count == size;
    }

    public void print(){
        if(isEmpty()){
            System.out.println("Stack is empty !");
        }
        else{
            Node temp = top;
            while (temp != null){
                System.out.print(temp.data+" -> ");
                temp = temp.next;
            }
            System.out.print("*");
        }
    }

}
