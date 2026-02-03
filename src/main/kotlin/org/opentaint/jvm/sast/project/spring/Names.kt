package org.opentaint.jvm.sast.project.spring

const val SpringPackage = "org.springframework"

val springControllerClassAnnotations = setOf(
    "$SpringPackage.stereotype.Controller",
    "$SpringPackage.web.bind.annotation.RestController",
)

const val springControllerRequestMapping = "$SpringPackage.web.bind.annotation.RequestMapping"

val springControllerMethodMappingAnnotations = setOf(
    "$SpringPackage.web.bind.annotation.GetMapping",
    "$SpringPackage.web.bind.annotation.PostMapping",
    "$SpringPackage.web.bind.annotation.PutMapping",
    "$SpringPackage.web.bind.annotation.DeleteMapping",
    "$SpringPackage.web.bind.annotation.PatchMapping",
)

const val SpringModelAttribute = "$SpringPackage.web.bind.annotation.ModelAttribute"
const val SpringPathVariable = "$SpringPackage.web.bind.annotation.PathVariable"
const val SpringRequestParam = "$SpringPackage.web.bind.annotation.RequestParam"
const val SpringRequestBody = "$SpringPackage.web.bind.annotation.RequestBody"

const val SpringValidator = "$SpringPackage.validation.Validator"
const val SpringBindingResult = "$SpringPackage.validation.BindingResult"
const val SpringBeanBindingResult = "$SpringPackage.validation.BeanPropertyBindingResult"

const val SpringAutowired = "$SpringPackage.beans.factory.annotation.Autowired"

const val ReactorMono = "reactor.core.publisher.Mono"
const val ReactorFlux = "reactor.core.publisher.Flux"

const val JakartaConstraint = "jakarta.validation.Constraint"
const val JakartaValid = "jakarta.validation.Valid"

const val SpringRepository = "$SpringPackage.data.repository.Repository"
