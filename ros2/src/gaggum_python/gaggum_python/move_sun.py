import rclpy
from rclpy.node import Node
from geometry_msgs.msg import PoseStamped, Twist
from nav_msgs.msg import Odometry
from std_msgs.msg import Int16

class moveSun(Node):
    def __init__(self):
        super().__init__('move_sun')
    # subscriber-----------------------------------
        self.odom_sub = self.create_subscription(Odometry, '/odom', self.odom_callback, 10)
        self.twist_sub = self.create_subscription(Twist, '/cmd_vel', self.twist_callback, 10)
        self.move_sun_sub = self.create_subscription(Int16, '/move_sun', self.move_sun_callback, 10)
    
    # publisher------------------------------------
        self.goal_pose_pub = self.create_publisher(PoseStamped, '/goal_pose', 10)
        self.twist_pub = self.create_publisher(Twist, '/cmd_vel', 10)

    # timer_callback
        timer_period = 0.5  # seconds
        self.timer = self.create_timer(timer_period, self.timer_callback)
        
    # callback 상태 확인용 ---------------------------
        self.is_odom = False        # Odometry
        self.is_plan = False        # Path
        self.is_move = True         # 터틀봇이 이동 가능한 상태
        self.is_twist = False       # Twist
        self.is_move_sun = False    # 햇빛 이동

    # 메시지-----------------------------------------
        self.pose_msg = PoseStamped()   # nav2에 목표 좌표 보냄
        self.odom_msg = Odometry()      # odom
        self.twist_msg = Twist()        # 터틀봇 움직임 제어
        self.move_sun_msg = Int16       # 햇빛 이동

    # 데이터-----------------------------------------
        
        # 백에서 넘어오는 trigger 더미

    def timer_callback(self):
        pass

    # callback 함수 ---------------------------------
    def odom_callback(self, msg):
        self.is_odom = True
        self.odom_msg = msg

    def twist_callback(self, msg):
        self.is_twist = True
        self.twist_msg = msg

    def move_sun_callback(self, msg):
        self.is_move_sun = True
        self.move_sun_msg = msg
        print(msg)

def main(args=None):
    rclpy.init(args=args)

    move_sun = moveSun()

    rclpy.spin(move_sun)

    move_sun.destroy_node()
    rclpy.shutdown()


if __name__ == '__main__':
    main()