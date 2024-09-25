package org.opentaint.ir.impl.hierarchies;

public interface Creature {

    void say(String smth);

    default void hello() {
        say("Hello");
    }

    interface Animal extends Creature {
    }

    interface Fish extends Creature {
        @Override
        void say(String smth);
    }

    interface Dinosaur extends Creature, Animal {
    }

    interface Bird extends Creature {
    }

    class DinosaurImpl implements Dinosaur {
        @Override
        public void say(String smth) {
            System.out.println("Dino say:" + smth);
        }
    }

    class TRex extends DinosaurImpl implements Dinosaur {
        @Override
        public void say(String smth) {
            super.say("TRex say:" + smth);
        }

        @Override
        public void hello() {
            // do nothing
        }
    }

    class Pterodactyl extends DinosaurImpl implements Bird {
        @Override
        public void say(String smth) {
            super.say("Pterodactyl say:" + smth);
        }
    }


}
