package org.opentaint.ir.impl.hierarchies;

public interface Creature {

    public static interface Animal extends Creature {
    }

    public static interface Fish extends Creature {
    }

    public static interface Dinosaur extends Creature, Animal {
    }

    public static interface Bird extends Creature {
    }

    public static class DinosaurImpl implements Dinosaur {
    }

    public static class TRex extends DinosaurImpl implements Dinosaur {
    }

    public static class Pterodactyl extends DinosaurImpl implements Bird {
    }


}
