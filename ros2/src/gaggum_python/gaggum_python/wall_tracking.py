import rclpy
from rclpy.node import Node

from geometry_msgs.msg import Twist, Point, Point32
# from gaggum_msgs.msg import MapScan
from squaternion import Quaternion
from nav_msgs.msg import Odometry,Path
from sensor_msgs.msg import LaserScan, PointCloud
from rclpy.qos import qos_profile_sensor_data, QoSProfile

class wallTracking(Node):

    def __init__(self) :

        super().__init__('wall_Tracking')
        self.cmd_pub = self.create_publisher(Twist,'cmd_vel',10)
        self.lidar_sub = self.create_subscription(LaserScan,'/scan',self.lidar_callback, qos_profile_sensor_data)
        self.subscription = self.create_subscription(Odometry,'/odom',self.odom_callback,10)
        self.twist_sub= self.create_subscription(Twist,'/cmd_vel',self.twist_callback,10)

        # 맵 만들 때 필요한 변수를 저장하는 주소 publish
        self.map_scan_publisher = self.create_publisher(MapScan, '/map_scan', 100)
        # socket에서 받아온 맵 만들기 실행 여부 정보 받기
        self.create_map_sub = self.create_subscription(MapScan, '/MapScan', self.map_scan_callback, 100)

        self.cmd_msg = Twist()
        time_period = 0.1
        self.timer = self.create_timer(time_period,self.timer_callback)

        self.is_odom=False
        self.is_path=True
        self.is_status=False
        self.is_lidar = False

        # 터틀봇의 상태를 저장
        self.state = 0

        self.is_comback = False
        self.is_mapping_end = False

        # 영역별 장애물과의 거리
        self.regions = {
            'left': 0,
            'front_left': 0,
            'front_right': 0,
            'front': 0,
            'right': 0,
        }
        # 장애물 충돌여부
        self.collision = False

        # wall_following 시작 조건
        self.is_start = False


    # wall_tracking 작동하기 위한 함수
    def map_scan_callback(self, msg):

        # map_scan은 0 or 1로 들어옴
        self.is_start = msg.run

        print("wall_tracking 데이터 값", msg.run)
   
    def timer_callback(self):
        # 맵핑 종료 조건
        #print(self.is_odom, self.is_path, self.is_status, self.is_lidar)
        if self.is_odom and self.is_lidar:
            # 로봇의 현재 위치
            robot_pose_x = self.odom_msg.pose.pose.position.x
            robot_pose_y = self.odom_msg.pose.pose.position.y
            #print(robot_pose_x, robot_pose_y)
            #print(self.is_comback, self.is_mapping_end)
            # 로봇이 시작 위치 (-5.818, 6.399)에 5 반경을 벗어나면
            if not (-5.818 - 0.3 <= robot_pose_x <= -5.818 + 0.3 and 6.399 - 0.3 <= robot_pose_y <= 6.399 + 0.3):
                self.is_comback = True
            elif self.is_comback:
                self.is_mapping_end = True
                self.is_comback = False
                self.is_start = False
        
        
        print('front  :', self.regions['front'])
        print('f_right:', self.regions['front_right'])
        print('right  :', self.regions['right'])
        print('r_t_c  :', self.regions['right_turn_check'])
        print('left   :', self.regions['left'])
        print('=================================================')

        if self.is_start and not self.is_mapping_end:
            if self.state == 0:
                self.find_wall()
            elif self.state == 1:
                self.turn_left()
            elif self.state == 2:
                self.follow_the_wall()
            elif self.state == 3:
                self.turn_right()
            else:
                print('오류 발생!!')

        # 맵 종료 되면 -1 data 전달. why? 종료하기 위해서.
        elif self.is_mapping_end:
            print("맵 스캔이 종료되었습니다!!!")
            msg = MapScan()
            msg.run = -1
            self.map_scan_publisher.publish(msg)
            
        #     self.is_mapping_end = False

            
        else:
            # 제자리에 멈추기
            self.cmd_msg.linear.x = 0.0
            self.cmd_msg.angular.z = 0.0
            #print('터틀봇 대기중')      
            

        self.cmd_pub.publish(self.cmd_msg)

    def change_state(self,state):

        if state is not self.state:
            self.state = state


    def take_action(self):
        
        if self.regions['front'] > 0.4:                         # 전방이 크면
            if self.regions['front_right'] < 0.3:               # 전방 오른쪽이 작으면
                self.change_state(1)                            # 왼쪽으로 돌아라(집게 충돌 방지)
            else:
                if self.regions['right'] > 0.6:                 # 오른쪽이 크면
                    self.change_state(0)
                    if self.regions['right_turn_check'] > 0.1:  # 오른쪽에 벽이 없으면
                        self.change_state(3)                    # 오른쪽으로 돌아라
                else:
                    self.change_state(2)            
        elif self.regions['front'] < 0.4:           # 전방이 작으면
            self.change_state(1)                    # 왼쪽으로 돌아라
            if self.regions['right'] > 1.0 and self.regions['left'] < 0.3:         # 오른쪽이 크면
                self.change_state(3)

        else:
            print('예외상황 발생!!')


    def find_wall(self):

        self.cmd_msg.linear.x = 0.22
        self.cmd_msg.angular.z = -0.1

    
    def turn_left(self):

        self.cmd_msg.angular.z = 0.22

    
    def follow_the_wall(self):

        self.cmd_msg.linear.x = 0.2

    def turn_right(self):
        self.cmd_msg.angular.z = -0.22
    
    def odom_callback(self, msg):

        # 제자리에 멈추고 행동 취하기 충돌 방지
        self.cmd_msg.linear.x = 0.0
        self.cmd_msg.angular.z = 0.0

        self.is_odom=True
        self.odom_msg=msg
        q = Quaternion(msg.pose.pose.orientation.w, msg.pose.pose.orientation.x, msg.pose.pose.orientation.y, msg.pose.pose.orientation.z)
        _,_,self.robot_yaw = q.to_euler()


    def scan_callback(self, msg):
        self.is_lidar = True
        self.lidar_msg = msg
        self.regions = {
            'front': min(msg.ranges[:11] + msg.ranges[350:360]),
            'front_right': min(msg.ranges[320:350]),
            'right_turn_check': min(msg.ranges[180:360]),
            'right': min(msg.ranges[270:280]),
            'left': min(msg.ranges[70:90]),
        }

        self.take_action()


    def twist_callback(self,msg):
        self.is_status=True
        self.status_msg=msg
    

def main(args=None):

    rclpy.init(args=args)
    Wall_tracking = wallTracking()
    rclpy.spin(Wall_tracking)
    Wall_tracking.destroy_node()
    rclpy.shutdown()

if __name__ == '__main__':
    main()