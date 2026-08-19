# Client 9.2.2 protocol evidence overlay

Each JSON file contains a top-level commands array. Command objects require:

- hexId, decimalId, names;
- evidence, webStatus, androidStatus;
- clientSources using paths relative to the decompiled client root;
- fields with path, name, rawTypes, nullable, unit, evidence,
  businessApproved, and clientSources.

Evidence files record only client-confirmed or explicitly capture-confirmed
semantics. Unknown fields are omitted instead of guessed. Paths and line ranges
are validated by the generator.
