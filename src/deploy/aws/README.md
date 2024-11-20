## Deploy Masters of Java on AWS

Create the following private ECR repositories in your AWS account:
- `moj/moj-single`
- `moj/moj-controller`
- `moj/moj-worker`

Adjust the environment variables in the following commands to match your AWS setup:
```
$ export REGISTRY=598172618529.dkr.ecr.eu-west-1.amazonaws.com
$ export REGISTRY_USERNAME=AWS
$ export REGISTRY_PASSWORD=$(aws ecr get-login-password --region eu-west-1)
```

Then build and push the images to ECR:    
```
$ mvn -Dmoj.image.base=${REGISTRY}/moj/moj -Pregistry-build -Dmaven.test.skip clean deploy
```

Then deploy the stack using CDK:
```
$ cdk bootstrap
$ cdk deploy
```