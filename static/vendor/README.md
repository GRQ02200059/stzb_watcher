# Vendored Dashboard Libraries

These browser distributions are pinned locally so the dashboard and export
features continue to work without Google Fonts or jsDelivr access.

| Package | File | License | SHA-256 |
|---|---|---|---|
| ExcelJS 4.4.0 | `exceljs-4.4.0.min.js` | MIT (`exceljs.LICENSE`) | `7e49da68588e250dbb8bba190d2caa8ab3787cc0284bda1d8b2f805c4df742c9` |
| jsPDF 2.5.1 | `jspdf-2.5.1.umd.min.js` | MIT (`jspdf.LICENSE`) | `98ccf17aa10c20bb1301762618fcc9b6ab3a4e7f26b6071d64d0b41154df3875` |
| jsPDF-AutoTable 3.8.2 | `jspdf-autotable-3.8.2.min.js` | MIT (`jspdf-autotable.LICENSE`) | `27a9c3b61843c6312b87f142d40fe77c0f0f054c9f3cdeccc4bfd5f3322859c8` |

Source URLs:

- `https://cdn.jsdelivr.net/npm/exceljs@4.4.0/dist/exceljs.min.js`
- `https://cdn.jsdelivr.net/npm/jspdf@2.5.1/dist/jspdf.umd.min.js`
- `https://cdn.jsdelivr.net/npm/jspdf-autotable@3.8.2/dist/jspdf.plugin.autotable.min.js`

When upgrading, update the filename, license text, source URL, hash, dashboard
script reference, and vendor regression test together.
