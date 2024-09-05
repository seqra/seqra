package org.opentaint.ir.impl

import org.opentaint.ir.api.ClasspathSet
import org.opentaint.ir.api.CompilationDatabase
import java.io.File

class CompilationDatabaseImpl: CompilationDatabase {

    override suspend fun classpathSet(locations: List<File>): ClasspathSet {
        TODO("Not yet implemented")
    }

    override suspend fun load(dirOrJar: File): CompilationDatabase {
        TODO("Not yet implemented")
    }

    override suspend fun load(dirOrJars: List<File>): CompilationDatabase {
        TODO("Not yet implemented")
    }

    override suspend fun refresh(): CompilationDatabase {
        TODO("Not yet implemented")
    }

    override fun watchFileSystemChanges(): CompilationDatabase {
        TODO("Not yet implemented")
    }

}