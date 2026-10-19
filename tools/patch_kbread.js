
const fs = require('fs');
const p = 'src/main/java/com/badmelon/farming/service/AssistantService.java';
let s = fs.readFileSync(p, 'utf8');
function rep(oldStr, newStr, expected) {
  const n = s.split(oldStr).length - 1;
  if (n !== expected) throw new Error('expected ' + expected + ' got ' + n + ' for ' + oldStr.slice(0,60));
  s = s.split(oldStr).join(newStr);
}
rep('            if (docs == null || docs.isEmpty()) {\n                HashMap<String, Object> resp = new HashMap<String, Object>();\n',
'            if (docs == null || docs.isEmpty()) {\n                if (hasFileExt) {\n                    return null;\n                }\n                HashMap<String, Object> resp = new HashMap<String, Object>();\n', 1);
rep('            String lowerKeyword = keyword.toLowerCase();\n',
'            String lowerKeyword = keyword.toLowerCase();\n            boolean fileNameMatched = false;\n            for (KnowledgeDoc d : docs) {\n                String fn = d.getFileName();\n                if (fn != null && fn.toLowerCase().contains(lowerKeyword)) {\n                    fileNameMatched = true;\n                    break;\n                }\n            }\n            if (hasFileExt && !fileNameMatched) {\n                return null;\n            }\n', 1);
fs.writeFileSync(p, s);
console.log('patched assistant, bytes=' + Buffer.byteLength(s));
