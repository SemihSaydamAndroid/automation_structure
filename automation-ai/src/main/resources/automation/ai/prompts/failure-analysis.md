Failed test: {{testName}}
Layer: {{layer}}
Rule-based hint: {{ruleHint}}

## Error
{{error}}

## Evidence
{{artifacts}}

Return exactly this JSON shape:
{"category": "PRODUCT_BUG | TEST_BUG | LOCATOR_CHANGED | ENVIRONMENT | TEST_DATA | FLAKY | UNKNOWN",
 "confidence": 0.0-1.0,
 "summary": "one sentence for the report",
 "rootCause": "most likely cause, referencing the evidence",
 "suggestedFix": "the concrete next action (who should do what)"}
