package org.opentaint.ir.impl.hierarchies;

public interface Creature {

    interface Animal extends Creature {
    }

    interface Fish extends Creature {
    }

    interface Dinosaur extends Creature, Animal {
    }

    interface Bird extends Creature {
    }

    class DinosaurImpl implements Dinosaur {
    }

    class TRex extends DinosaurImpl implements Dinosaur {
    }

    class Pterodactyl extends DinosaurImpl implements Bird {
    }


}
