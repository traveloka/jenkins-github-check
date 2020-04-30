# Jenkins GitHub Check Integration

This plugin aim to integrate GitHub Check with jenkins.

## Requirement

- This plugin only support multi branch project with github as source scm.
- You must use GitHub App as credential.

## Configuration

![image](https://user-images.githubusercontent.com/1484485/80692361-a6516d00-8afb-11ea-9fa2-62a44993b056.png)

## Custom Steps

### setCheckRunOutput

Syntax:

```groovy
# Jenkinsfile
setCheckRunOutput(file: "github-check.json")
```

github-check.json

```json
{
  "text": "Custom check output",
  "annotations": [...]
}
```

Please refer to https://developer.github.com/v3/checks/runs/#create-a-check-run for full documentation
