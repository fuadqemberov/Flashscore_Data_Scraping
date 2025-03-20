package flashscore.weeklydatascraping.fffffff;

public class main {
    public static void main(String[] args) {

        String soz = "ata";
        MyStack my = new MyStack(soz.length());
        QueueExample example = new QueueExample(soz.length());

        for (char c : soz.toCharArray()) {
            my.push(c);
            example.enQueue(c);
        }

        int count = 0;
        while (!my.isEmpty() && !example.isEmpty()) {
            if (my.pop() == example.deQueue()) {
                count++;
            }
        }
        if (count == soz.length()) {
            System.out.println("Bu soz palindromdur : " + soz);
        } else {
            System.out.println("Bu soz polidrom deyil : " + soz);
        }

    }
}
