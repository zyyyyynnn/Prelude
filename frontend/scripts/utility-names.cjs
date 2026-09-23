#!/usr/bin/env node
'use strict'

/* The class name a `@utility` block registers. `workspace-page__content` and `app-layout__main`
   are BEM element names, and an underscore is legal in a utility name — a character class without
   it silently drops exactly those two declarations, which is how the page shells escaped the
   cascade audit while every other surface was inside it. Every gate that asks "is this class a
   registered utility?" reads it through here, so the answer cannot differ between scripts. */
const UTILITY_NAME = '[a-z0-9_*-]+'

const utilityPattern = (flags) => new RegExp(`^@utility\\s+(${UTILITY_NAME})`, flags)

const utilityPositions = (css) => [...css.matchAll(utilityPattern('gm'))]

const declaredUtilities = (css) => utilityPositions(css).map((match) => match[1])

/* A functional utility is declared as `name-*`; the class a call site writes is `name-<value>`,
   so the family is the declared name without its wildcard. */
const utilityFamily = (utilityName) => utilityName.replace(/-\*$/, '')

module.exports = { UTILITY_NAME, utilityPositions, declaredUtilities, utilityFamily }
