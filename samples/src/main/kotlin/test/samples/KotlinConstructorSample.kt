package test.samples

class KotlinConstructorSample {
    private var name: String
    private var value: Int

    /*noArgConstructor:start*/constructor()/*noArgConstructor:end*/ {
        this.name = "default"
        this.value = 0
    }

    /*constructorEntry:start*/constructor(name: String)/*constructorEntry:end*/ {
        this.name = name
        this.value = 0
    /*constructorExit:start*/}/*constructorExit:end*/

    constructor(name: String, value: Int) {
        this.name = name
        this.value = value
    }
}
