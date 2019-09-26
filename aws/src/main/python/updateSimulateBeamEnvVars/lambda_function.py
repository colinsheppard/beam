# coding=utf-8
import time
import json
import boto3
import logging
from botocore.errorfactory import ClientError

logger = logging.getLogger()
logger.setLevel(logging.INFO)

def lambda_handler(event, context):
    image_ids = {}
    image_ids['us-east-2'] = event.get('ami_id')
    image_ids['us-east-1'] = event.get('ami_id_us_east_1')
    image_ids['us-west-2'] = event.get('ami_id_us_west_2')

    update_lambda(image_ids)

    return "Update complete"

def update_lambda(image_ids):
    lm = boto3.client('lambda')
    logger.info('updating image ids ' + str(image_ids))
    en_var = lm.get_function_configuration(FunctionName='simulateBeam')['Environment']['Variables']
    en_var.update({
        'us_east_2_IMAGE_ID': image_ids['us-east-2'],
        'us_east_1_IMAGE_ID': image_ids['us-east-1'],
        'us_west_2_IMAGE_ID': image_ids['us-west-2']
    })
    lm.update_function_configuration(
        FunctionName='simulateBeam',
        Environment={
            'Variables': en_var
        }
    )
    logger.info('simulateBeam image ids updated')